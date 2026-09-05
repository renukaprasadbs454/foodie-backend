package com.foodie.payment.service.impl;

import com.foodie.common.enums.UserType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.enums.RefundStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.cashfree.CashfreePaymentClient;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.dto.response.PaymentInitiationResponseDto;
import com.foodie.payment.dto.response.RefundInitiationResponseDto;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.entity.RefundRequest;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.repository.RefundRequestRepository;
import com.foodie.payment.service.PaymentIdempotencyStore;
import com.foodie.payment.service.PaymentService;
import com.foodie.payment.service.WebhookDedupService;
import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.shared.contract.OrderPaymentPort;
import com.foodie.shared.event.PaymentCapturedEvent;
import com.foodie.shared.event.PaymentFailedEvent;
import com.foodie.shared.event.RefundProcessedEvent;
import com.foodie.wallet.service.WalletService;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.LedgerReferenceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    public static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PaymentRepository paymentRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final OrderPaymentPort orderPaymentPort;
    private final CustomerSummaryProvider customerSummaryProvider;
    private final CashfreePaymentClient cashfreeClient;
    private final WebhookDedupService webhookDedupService;
    private final PaymentIdempotencyStore idempotencyStore;
    private final String appId;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final WalletService walletService;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            RefundRequestRepository refundRequestRepository,
            OrderPaymentPort orderPaymentPort,
            CustomerSummaryProvider customerSummaryProvider,
            CashfreePaymentClient cashfreeClient,
            WebhookDedupService webhookDedupService,
            PaymentIdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            WalletService walletService,
            @Value("${CASHFREE_APP_ID:${foodie.payment.cashfree.client-id:TEST11201264a9f4217dcbbe3eef910f46210211}}") String appId) {
        this.paymentRepository = paymentRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.orderPaymentPort = orderPaymentPort;
        this.customerSummaryProvider = customerSummaryProvider;
        this.cashfreeClient = cashfreeClient;
        this.webhookDedupService = webhookDedupService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.walletService = walletService;
        this.appId = appId;
    }

    @Override
    @Transactional
    public PaymentInitiationResponseDto initiate(UUID userCredentialId, UUID orderId, String idempotencyKey,
            boolean useWallet) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required.");
        }

        var cached = idempotencyStore.find(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        var byKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            PaymentInitiationResponseDto view = toInitiationView(byKey.get());
            idempotencyStore.store(idempotencyKey, view);
            return view;
        }

        UUID customerId = customerSummaryProvider.findByUserCredentialId(userCredentialId)
                .map(CustomerSummaryProvider.CustomerSummary::customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found."));

        OrderPaymentPort.PayableOrder order = orderPaymentPort.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found."));

        if (!order.customerId().equals(customerId)) {
            throw new ResourceNotFoundException("Order not found.");
        }
        if (order.status() != OrderStatus.PLACED) {
            throw new UnprocessableEntityException(
                    ErrorCode.ORDER_NOT_PAYABLE, "Order is not payable in its current status.");
        }

        BigDecimal totalAmount = order.totalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal walletAmountUsed = BigDecimal.ZERO;
        BigDecimal razorpayAmount = totalAmount;

        if (useWallet) {
            BigDecimal walletBalance = walletService.getBalance(userCredentialId, UserType.CUSTOMER).balance();
            if (walletBalance.compareTo(BigDecimal.ZERO) > 0) {
                walletAmountUsed = walletBalance.min(totalAmount);
                razorpayAmount = totalAmount.subtract(walletAmountUsed);
            }
        }

        CustomerSummaryProvider.CustomerSummary customer = customerSummaryProvider.findByUserCredentialId(userCredentialId).orElse(null);
        String customerPhone = "9999999999";

        var existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PENDING) {
                PaymentInitiationResponseDto view = toInitiationView(payment);
                idempotencyStore.store(idempotencyKey, view);
                return view;
            }
            if (payment.getStatus() == PaymentStatus.FAILED) {
                String paymentSessionId = null;
                String cfOrderId = null;
                if (razorpayAmount.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        var created = cashfreeClient.createOrder(razorpayAmount, customerId.toString(), customerPhone, orderId.toString());
                        paymentSessionId = created.paymentSessionId();
                        cfOrderId = created.cfOrderId();
                    } catch (Exception ex) {
                        log.error("Cashfree order creation error: {}", ex.getMessage(), ex);
                        cfOrderId = "CF_LOCAL_" + orderId.toString().substring(0, 8);
                        paymentSessionId = "session_local_" + System.currentTimeMillis();
                    }
                }

                if (walletAmountUsed.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.debit(OwnerType.CUSTOMER, customerId, walletAmountUsed,
                            LedgerReferenceType.ORDER_PAYMENT, orderId);
                }

                payment.reinitiate(paymentSessionId, idempotencyKey, razorpayAmount, walletAmountUsed);
                if (razorpayAmount.compareTo(BigDecimal.ZERO) == 0 && walletAmountUsed.compareTo(BigDecimal.ZERO) > 0) {
                    payment.markCaptured("WALLET_" + orderId);
                    paymentRepository.save(payment);
                    eventPublisher.publishEvent(PaymentCapturedEvent.of(payment.getOrderId(), payment.getId()));
                } else {
                    if (cfOrderId != null) payment.setCashfreeOrderId(cfOrderId);
                    paymentRepository.save(payment);
                }

                PaymentInitiationResponseDto view = toInitiationView(payment);
                idempotencyStore.store(idempotencyKey, view);
                return view;
            }
            throw new UnprocessableEntityException(
                    ErrorCode.ORDER_NOT_PAYABLE, "Order already has a captured or refunded payment.");
        }

        String paymentSessionId = null;
        String cfOrderId = null;
        if (razorpayAmount.compareTo(BigDecimal.ZERO) > 0) {
            try {
                var created = cashfreeClient.createOrder(razorpayAmount, customerId.toString(), customerPhone, orderId.toString());
                paymentSessionId = created.paymentSessionId();
                cfOrderId = created.cfOrderId();
            } catch (Exception ex) {
                log.error("Cashfree order creation error: {}", ex.getMessage(), ex);
                cfOrderId = "CF_LOCAL_" + orderId.toString().substring(0, 8);
                paymentSessionId = "session_local_" + System.currentTimeMillis();
            }
        }

        if (walletAmountUsed.compareTo(BigDecimal.ZERO) > 0) {
            walletService.debit(OwnerType.CUSTOMER, customerId, walletAmountUsed, LedgerReferenceType.ORDER_PAYMENT,
                    orderId);
        }

        Payment payment = paymentRepository.save(Payment.initiate(
                orderId, paymentSessionId, razorpayAmount, walletAmountUsed, idempotencyKey));
        if (cfOrderId != null) {
            payment.setCashfreeOrderId(cfOrderId);
            paymentRepository.save(payment);
        }

        if (razorpayAmount.compareTo(BigDecimal.ZERO) == 0 && walletAmountUsed.compareTo(BigDecimal.ZERO) > 0) {
            eventPublisher.publishEvent(PaymentCapturedEvent.of(payment.getOrderId(), payment.getId()));
        }

        PaymentInitiationResponseDto view = toInitiationView(payment);
        idempotencyStore.store(idempotencyKey, view);
        return view;
    }

    @Override
    @Transactional
    public boolean verifyPayment(UUID userCredentialId,
            com.foodie.payment.dto.request.VerifyPaymentRequestDto request) {
        
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for order."));

        String cfOrderId = request.cashfreeOrderId();
        if (cfOrderId == null || cfOrderId.isBlank()) {
            cfOrderId = payment.getCashfreeOrderId();
        }

        boolean isPaid = false;
        if (cfOrderId != null && !cfOrderId.isBlank()) {
            try {
                var fetch = cashfreeClient.fetchOrder(cfOrderId);
                if ("PAID".equalsIgnoreCase(fetch.status()) || "ACTIVE".equalsIgnoreCase(fetch.status()) || "SUCCESS".equalsIgnoreCase(fetch.status())) {
                    isPaid = true;
                }
            } catch (Exception ex) {
                log.warn("Cashfree fetch order check fallback for order {}: {}", request.orderId(), ex.getMessage());
                // Fallback to stored cashfreeOrderId on payment entity
                if (payment.getCashfreeOrderId() != null && !payment.getCashfreeOrderId().equals(cfOrderId)) {
                    try {
                        var fetch = cashfreeClient.fetchOrder(payment.getCashfreeOrderId());
                        if ("PAID".equalsIgnoreCase(fetch.status()) || "ACTIVE".equalsIgnoreCase(fetch.status()) || "SUCCESS".equalsIgnoreCase(fetch.status())) {
                            isPaid = true;
                            cfOrderId = payment.getCashfreeOrderId();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (!isPaid) {
            log.info("Payment not yet verified as PAID for orderId={}, cfOrderId={}", request.orderId(), cfOrderId);
            return false;
        }

        if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.FAILED) {
            payment.markCaptured(cfOrderId != null ? cfOrderId : request.cashfreeOrderId());
            paymentRepository.save(payment);
            eventPublisher.publishEvent(PaymentCapturedEvent.of(
                    payment.getOrderId(),
                    payment.getId()));
        }
        return true;
    }

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        // Implementation for Cashfree Webhook can be added here
        log.info("Received cashfree webhook");
    }


    @Override
    @Transactional
    public RefundInitiationResponseDto refund(
            UUID paymentId,
            RefundPaymentRequestDto request,
            UUID actorId,
            boolean systemActor) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new UnprocessableEntityException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE, "Payment is not refundable in its current status.");
        }
        if (payment.getCashfreeOrderId() == null || payment.getCashfreeOrderId().isBlank() || payment.getCashfreeOrderId().startsWith("WALLET_")) {
            throw new UnprocessableEntityException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE, "Payment has no Cashfree order id for refund.");
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED, "Refund amount cannot exceed captured payment amount.");
        }

        var pending = refundRequestRepository.findByPaymentIdAndStatus(paymentId, RefundStatus.INITIATED);
        if (!pending.isEmpty()) {
            RefundRequest existing = pending.getFirst();
            log.info("Returning existing INITIATED refundRequestId={} for paymentId={}",
                    existing.getId(), paymentId);
            return new RefundInitiationResponseDto(existing.getId(), existing.getStatus());
        }

        var cashfreeRefund = cashfreeClient.createRefund(
                payment.getCashfreeOrderId(), amount, request.reason());

        UUID initiator = actorId != null ? actorId : SYSTEM_ACTOR_ID;
        RefundRequest refundRequest = refundRequestRepository.save(RefundRequest.initiate(
                paymentId, amount, request.reason(), initiator, cashfreeRefund.cfRefundId()));

        log.info(
                "Refund initiated refundRequestId={} paymentId={} amount={} systemActor={} actorId={}",
                refundRequest.getId(), paymentId, amount, systemActor, initiator);

        return new RefundInitiationResponseDto(refundRequest.getId(), refundRequest.getStatus());
    }

    private void onPaymentCaptured(JsonNode root) {
        JsonNode data = root.path("data").path("payment");
        String cfOrderId = data.path("order_id").asText(null);
        if (cfOrderId == null) {
            log.warn("payment.captured missing order_id — ignored");
            return;
        }
        Payment payment = paymentRepository.findByCashfreeOrderId(cfOrderId).orElse(null);
        if (payment == null) {
            log.warn("payment.captured for unknown cashfreeOrderId={}", cfOrderId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            log.warn("payment.captured ignored for status={}", payment.getStatus());
            return;
        }
        payment.markCaptured(cfOrderId);
        eventPublisher.publishEvent(PaymentCapturedEvent.of(payment.getOrderId(), payment.getId()));
        log.info("Payment CAPTURED paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    private void onPaymentFailed(JsonNode root) {
        JsonNode data = root.path("data").path("payment");
        String cfOrderId = data.path("order_id").asText(null);
        if (cfOrderId == null) {
            return;
        }
        Payment payment = paymentRepository.findByCashfreeOrderId(cfOrderId).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.markFailed(cfOrderId);
        paymentRepository.save(payment);

        if (payment.getWalletAmount() != null && payment.getWalletAmount().compareTo(BigDecimal.ZERO) > 0) {
            OrderPaymentPort.PayableOrder order = orderPaymentPort.findByOrderId(payment.getOrderId()).orElse(null);
            if (order != null) {
                walletService.credit(OwnerType.CUSTOMER, order.customerId(), payment.getWalletAmount(),
                        LedgerReferenceType.ORDER_PAYMENT, payment.getId());
            }
        }

        eventPublisher.publishEvent(PaymentFailedEvent.of(payment.getOrderId(), payment.getId()));
        log.info("Payment FAILED paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    private void onRefundProcessed(JsonNode root) {
        JsonNode data = root.path("data").path("refund");
        String cfRefundId = data.path("cf_refund_id").asText(null);
        String cfOrderId = data.path("order_id").asText(null);
        if (cfRefundId == null && cfOrderId == null) {
            return;
        }

        RefundRequest refundRequest = null;
        if (cfRefundId != null) {
            refundRequest = refundRequestRepository.findByCashfreeRefundId(cfRefundId).orElse(null);
        }
        Payment payment = null;
        if (refundRequest != null) {
            payment = paymentRepository.findById(refundRequest.getPaymentId()).orElse(null);
        } else if (cfOrderId != null) {
            payment = paymentRepository.findByCashfreeOrderId(cfOrderId).orElse(null);
            if (payment != null) {
                refundRequest = refundRequestRepository
                        .findByPaymentIdAndStatus(payment.getId(), RefundStatus.INITIATED)
                        .stream()
                        .findFirst()
                        .orElse(null);
            }
        }
        if (payment == null || refundRequest == null) {
            log.warn("refund.processed unmatched cfRefundId={}", cfRefundId);
            return;
        }
        if (refundRequest.getStatus() == RefundStatus.PROCESSED) {
            return;
        }
        refundRequest.markProcessed();
        payment.markRefunded();
        eventPublisher.publishEvent(RefundProcessedEvent.of(
                payment.getId(), refundRequest.getId(), refundRequest.getAmount()));
        log.info("Refund PROCESSED refundRequestId={} paymentId={}", refundRequest.getId(), payment.getId());
    }

    private PaymentInitiationResponseDto toInitiationView(Payment payment) {
        return new PaymentInitiationResponseDto(
                payment.getPaymentSessionId(),
                payment.getCashfreeOrderId(),
                payment.getAmount(),
                "INR",
                appId,
                payment.getWalletAmount(),
                payment.getStatus().name());
    }

    private static String shortReceipt(UUID orderId) {
        String compact = orderId.toString().replace("-", "");
        return compact.substring(0, Math.min(40, compact.length()));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
