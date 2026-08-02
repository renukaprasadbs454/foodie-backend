package com.foodie.payment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.enums.RefundStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.razorpay.RazorpayClient;
import com.foodie.infrastructure.razorpay.RazorpayProperties;
import com.foodie.infrastructure.razorpay.RazorpaySignatureVerifier;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureVerifier signatureVerifier;
    private final WebhookDedupService webhookDedupService;
    private final PaymentIdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentServiceImpl(
            PaymentRepository paymentRepository,
            RefundRequestRepository refundRequestRepository,
            OrderPaymentPort orderPaymentPort,
            CustomerSummaryProvider customerSummaryProvider,
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties,
            RazorpaySignatureVerifier signatureVerifier,
            WebhookDedupService webhookDedupService,
            PaymentIdempotencyStore idempotencyStore,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.refundRequestRepository = refundRequestRepository;
        this.orderPaymentPort = orderPaymentPort;
        this.customerSummaryProvider = customerSummaryProvider;
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.signatureVerifier = signatureVerifier;
        this.webhookDedupService = webhookDedupService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public PaymentInitiationResponseDto initiate(UUID userCredentialId, UUID orderId, String idempotencyKey) {
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

        BigDecimal amount = order.totalAmount().setScale(2, RoundingMode.HALF_UP);
        var existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.PENDING) {
                PaymentInitiationResponseDto view = toInitiationView(payment);
                idempotencyStore.store(idempotencyKey, view);
                return view;
            }
            if (payment.getStatus() == PaymentStatus.FAILED) {
                var created = razorpayClient.createOrder(amount, shortReceipt(orderId), orderId.toString());
                payment.reinitiate(created.razorpayOrderId(), idempotencyKey, amount);
                paymentRepository.save(payment);
                PaymentInitiationResponseDto view = toInitiationView(payment);
                idempotencyStore.store(idempotencyKey, view);
                return view;
            }
            throw new UnprocessableEntityException(
                    ErrorCode.ORDER_NOT_PAYABLE, "Order already has a captured or refunded payment.");
        }

        var created = razorpayClient.createOrder(amount, shortReceipt(orderId), orderId.toString());
        Payment payment = paymentRepository.save(Payment.initiate(
                orderId, created.razorpayOrderId(), amount, idempotencyKey));
        PaymentInitiationResponseDto view = toInitiationView(payment);
        idempotencyStore.store(idempotencyKey, view);
        return view;
    }

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        if (!signatureVerifier.isValid(rawBody, signatureHeader)) {
            throw new BadRequestException(
                    ErrorCode.INVALID_WEBHOOK_SIGNATURE, "Invalid Razorpay webhook signature.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Malformed webhook payload.");
        }

        String eventId = firstNonBlank(root.path("id").asText(null), root.path("event_id").asText(null));
        if (webhookDedupService.isDuplicate(eventId)) {
            log.info("Duplicate Razorpay webhook acknowledged eventId={}", eventId);
            return;
        }

        String eventType = root.path("event").asText("");
        switch (eventType) {
            case "payment.captured" -> onPaymentCaptured(root);
            case "payment.failed" -> onPaymentFailed(root);
            case "refund.processed" -> onRefundProcessed(root);
            default -> log.info("Unrecognized Razorpay webhook event acknowledged type={}", eventType);
        }
        webhookDedupService.markProcessed(eventId);
    }

    @Override
    @Transactional
    public RefundInitiationResponseDto refund(
            UUID paymentId,
            RefundPaymentRequestDto request,
            UUID actorId,
            boolean systemActor
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new UnprocessableEntityException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE, "Payment is not refundable in its current status.");
        }
        if (payment.getRazorpayPaymentId() == null || payment.getRazorpayPaymentId().isBlank()) {
            throw new UnprocessableEntityException(
                    ErrorCode.PAYMENT_NOT_REFUNDABLE, "Payment has no Razorpay payment id for refund.");
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

        var razorpayRefund = razorpayClient.createRefund(
                payment.getRazorpayPaymentId(), amount, request.reason());

        UUID initiator = actorId != null ? actorId : SYSTEM_ACTOR_ID;
        RefundRequest refundRequest = refundRequestRepository.save(RefundRequest.initiate(
                paymentId, amount, request.reason(), initiator, razorpayRefund.razorpayRefundId()));

        log.info(
                "Refund initiated refundRequestId={} paymentId={} amount={} systemActor={} actorId={}",
                refundRequest.getId(), paymentId, amount, systemActor, initiator
        );

        return new RefundInitiationResponseDto(refundRequest.getId(), refundRequest.getStatus());
    }

    private void onPaymentCaptured(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String razorpayOrderId = entity.path("order_id").asText(null);
        String razorpayPaymentId = entity.path("id").asText(null);
        if (razorpayOrderId == null) {
            log.warn("payment.captured missing order_id — ignored");
            return;
        }
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (payment == null) {
            log.warn("payment.captured for unknown razorpayOrderId={}", razorpayOrderId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED || payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.FAILED) {
            log.warn("payment.captured ignored for status={}", payment.getStatus());
            return;
        }
        payment.markCaptured(razorpayPaymentId);
        eventPublisher.publishEvent(PaymentCapturedEvent.of(payment.getOrderId(), payment.getId()));
        log.info("Payment CAPTURED paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    private void onPaymentFailed(JsonNode root) {
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String razorpayOrderId = entity.path("order_id").asText(null);
        String razorpayPaymentId = entity.path("id").asText(null);
        if (razorpayOrderId == null) {
            return;
        }
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.markFailed(razorpayPaymentId);
        eventPublisher.publishEvent(PaymentFailedEvent.of(payment.getOrderId(), payment.getId()));
        log.info("Payment FAILED paymentId={} orderId={}", payment.getId(), payment.getOrderId());
    }

    private void onRefundProcessed(JsonNode root) {
        JsonNode entity = root.path("payload").path("refund").path("entity");
        String razorpayRefundId = entity.path("id").asText(null);
        String razorpayPaymentId = entity.path("payment_id").asText(null);
        if (razorpayRefundId == null && razorpayPaymentId == null) {
            return;
        }

        RefundRequest refundRequest = null;
        if (razorpayRefundId != null) {
            refundRequest = refundRequestRepository.findByRazorpayRefundId(razorpayRefundId).orElse(null);
        }
        Payment payment = null;
        if (refundRequest != null) {
            payment = paymentRepository.findById(refundRequest.getPaymentId()).orElse(null);
        } else if (razorpayPaymentId != null) {
            payment = paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).orElse(null);
            if (payment != null) {
                refundRequest = refundRequestRepository
                        .findByPaymentIdAndStatus(payment.getId(), RefundStatus.INITIATED)
                        .stream()
                        .findFirst()
                        .orElse(null);
            }
        }
        if (payment == null || refundRequest == null) {
            log.warn("refund.processed unmatched razorpayRefundId={}", razorpayRefundId);
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
                payment.getRazorpayOrderId(),
                payment.getAmount(),
                "INR",
                razorpayProperties.getKeyId()
        );
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
