package com.foodie.payment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.razorpay.RazorpayClient;
import com.foodie.infrastructure.razorpay.RazorpayProperties;
import com.foodie.infrastructure.razorpay.RazorpaySignatureVerifier;
import com.foodie.payment.dto.request.CreatePaymentRequestDto;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.dto.request.VerifyPaymentRequestDto;
import com.foodie.payment.dto.response.PaymentCreateResponseDto;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.gateway.PaymentGateway;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.repository.RefundRequestRepository;
import com.foodie.payment.service.PaymentIdempotencyStore;
import com.foodie.payment.service.WebhookDedupService;
import com.foodie.payment.service.impl.PaymentServiceImpl;
import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.shared.contract.OrderPaymentPort;
import com.foodie.shared.event.PaymentCapturedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private OrderPaymentPort orderPaymentPort;
    @Mock private CustomerSummaryProvider customerSummaryProvider;
    @Mock private RazorpayClient razorpayClient;
    @Mock private WebhookDedupService webhookDedupService;
    @Mock private PaymentIdempotencyStore idempotencyStore;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PaymentGateway paymentGateway;

    private PaymentServiceImpl service;
    private RazorpaySignatureVerifier signatureVerifier;
    private final UUID credentialId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_key");
        props.setWebhookSecret("whsec_test");
        signatureVerifier = new RazorpaySignatureVerifier(props);
        service = new PaymentServiceImpl(
                paymentRepository,
                refundRequestRepository,
                orderPaymentPort,
                customerSummaryProvider,
                razorpayClient,
                props,
                signatureVerifier,
                webhookDedupService,
                idempotencyStore,
                new ObjectMapper(),
                eventPublisher,
                paymentGateway
        );
    }

    @Test
    void createPayment_createsGatewayOrderAndPaymentRecord() {
        when(customerSummaryProvider.findByUserCredentialId(credentialId)).thenReturn(Optional.of(
                new CustomerSummaryProvider.CustomerSummary(customerId, "Customer", null)));
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, customerId, OrderStatus.PLACED, new BigDecimal("500.00"))));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentGateway.getGatewayName()).thenReturn("RAZORPAY");
        when(paymentGateway.createOrder(any())).thenReturn(
                new PaymentGateway.PaymentGatewayOrderResult("order_rzp_123", 50000L, new BigDecimal("500.00"), "INR", "rzp_test_key"));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setId(p, UUID.randomUUID());
            return p;
        });

        PaymentCreateResponseDto response = service.createPayment(credentialId, new CreatePaymentRequestDto(orderId), "k1");

        assertThat(response.gatewayOrderId()).isEqualTo("order_rzp_123");
        assertThat(response.amountPaise()).isEqualTo(50000L);
        assertThat(response.gateway()).isEqualTo("RAZORPAY");
        assertThat(response.keyId()).isEqualTo("rzp_test_key");
    }

    @Test
    void createPayment_orderNotOwned_throws404() {
        UUID otherCustomer = UUID.randomUUID();
        when(customerSummaryProvider.findByUserCredentialId(credentialId)).thenReturn(Optional.of(
                new CustomerSummaryProvider.CustomerSummary(customerId, "Customer", null)));
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, otherCustomer, OrderStatus.PLACED, new BigDecimal("500.00"))));

        assertThatThrownBy(() -> service.createPayment(credentialId, new CreatePaymentRequestDto(orderId), "k1"))
                .isInstanceOf(com.foodie.common.exception.ResourceNotFoundException.class);
    }

    @Test
    void createPayment_alreadyCaptured_throws422() {
        Payment payment = Payment.initiate(orderId, "order_rzp_123", new BigDecimal("500.00"), "k1");
        payment.markCaptured("pay_rzp_1");
        when(customerSummaryProvider.findByUserCredentialId(credentialId)).thenReturn(Optional.of(
                new CustomerSummaryProvider.CustomerSummary(customerId, "Customer", null)));
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, customerId, OrderStatus.PLACED, new BigDecimal("500.00"))));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.createPayment(credentialId, new CreatePaymentRequestDto(orderId), "k1"))
                .isInstanceOf(UnprocessableEntityException.class)
                .extracting(ex -> ((UnprocessableEntityException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_CAPTURED);
    }

    @Test
    void verifyPayment_validSignature_capturesPaymentAndPublishesEvent() {
        Payment payment = Payment.initiate(orderId, "order_rzp_123", new BigDecimal("500.00"), "k1");
        setId(payment, UUID.randomUUID());

        when(customerSummaryProvider.findByUserCredentialId(credentialId)).thenReturn(Optional.of(
                new CustomerSummaryProvider.CustomerSummary(customerId, "Customer", null)));
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, customerId, OrderStatus.PLACED, new BigDecimal("500.00"))));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyPaymentSignature(any())).thenReturn(true);

        VerifyPaymentRequestDto req = new VerifyPaymentRequestDto(orderId, "order_rzp_123", "pay_1", "sig_valid");
        boolean result = service.verifyPayment(credentialId, req);

        assertThat(result).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(eventPublisher).publishEvent(any(PaymentCapturedEvent.class));
    }

    @Test
    void verifyPayment_invalidSignature_throws400() {
        Payment payment = Payment.initiate(orderId, "order_rzp_123", new BigDecimal("500.00"), "k1");
        when(customerSummaryProvider.findByUserCredentialId(credentialId)).thenReturn(Optional.of(
                new CustomerSummaryProvider.CustomerSummary(customerId, "Customer", null)));
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, customerId, OrderStatus.PLACED, new BigDecimal("500.00"))));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.verifyPaymentSignature(any())).thenReturn(false);

        VerifyPaymentRequestDto req = new VerifyPaymentRequestDto(orderId, "order_rzp_123", "pay_1", "sig_bad");

        assertThatThrownBy(() -> service.verifyPayment(credentialId, req))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SIGNATURE_VERIFICATION_FAILED);
    }

    @Test
    void handleWebhook_invalidSignature_throws400() {
        assertThatThrownBy(() -> service.handleWebhook("{}", "bad"))
                .isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        verify(webhookDedupService, never()).markProcessed(any());
    }

    @Test
    void handleWebhook_duplicate_acknowledgesWithoutSideEffects() {
        String body = "{\"id\":\"evt_1\",\"event\":\"payment.captured\"}";
        String sig = RazorpaySignatureVerifier.hmacSha256Hex("whsec_test", body);
        when(webhookDedupService.isDuplicate("evt_1")).thenReturn(true);

        service.handleWebhook(body, sig);

        verify(paymentRepository, never()).findByRazorpayOrderId(any());
        verify(webhookDedupService, never()).markProcessed(any());
    }

    @Test
    void handleWebhook_paymentCaptured_publishesEvent() {
        Payment payment = Payment.initiate(orderId, "order_abc", new BigDecimal("10.00"), "k");
        setId(payment, UUID.randomUUID());
        String body = """
                {"id":"evt_2","event":"payment.captured","payload":{"payment":{"entity":{
                  "id":"pay_1","order_id":"order_abc","status":"captured"}}}}
                """;
        String sig = RazorpaySignatureVerifier.hmacSha256Hex("whsec_test", body);
        when(webhookDedupService.isDuplicate("evt_2")).thenReturn(false);
        when(paymentRepository.findByRazorpayOrderId("order_abc")).thenReturn(Optional.of(payment));

        TransactionSynchronizationManager.initSynchronization();
         try {
              service.handleWebhook(body, sig);
              verify(webhookDedupService, never()).markProcessed("evt_2");

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCommit());
             } finally {
        TransactionSynchronizationManager.clearSynchronization();
}

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentCapturedEvent.class);
        verify(webhookDedupService).markProcessed("evt_2");
    }

    @Test
    void refund_notCaptured_throws422() {
        Payment payment = Payment.initiate(orderId, "order_abc", new BigDecimal("10.00"), "k");
        setId(payment, UUID.randomUUID());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.refund(
                payment.getId(),
                new RefundPaymentRequestDto(new BigDecimal("10.00"), "cancel"),
                UUID.randomUUID(),
                false
        )).isInstanceOf(UnprocessableEntityException.class)
                .extracting(ex -> ((UnprocessableEntityException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_REFUNDABLE);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> type = entity.getClass();
            while (type != null) {
                try {
                    var field = type.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException ex) {
                    type = type.getSuperclass();
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
