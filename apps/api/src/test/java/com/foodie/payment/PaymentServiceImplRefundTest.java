package com.foodie.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OrderStatus;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.common.enums.RefundStatus;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.infrastructure.cashfree.CashfreePaymentClient;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.dto.response.RefundInitiationResponseDto;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.entity.RefundRequest;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.repository.RefundRequestRepository;
import com.foodie.payment.service.PaymentIdempotencyStore;
import com.foodie.payment.service.WebhookDedupService;
import com.foodie.payment.service.impl.PaymentServiceImpl;
import com.foodie.shared.contract.CustomerSummaryProvider;
import com.foodie.shared.contract.OrderPaymentPort;
import com.foodie.shared.event.RefundProcessedEvent;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplRefundTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private OrderPaymentPort orderPaymentPort;
    @Mock private CustomerSummaryProvider customerSummaryProvider;
    @Mock private CashfreePaymentClient cashfreeClient;
    @Mock private WebhookDedupService webhookDedupService;
    @Mock private PaymentIdempotencyStore idempotencyStore;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WalletService walletService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                refundRequestRepository,
                orderPaymentPort,
                customerSummaryProvider,
                cashfreeClient,
                webhookDedupService,
                idempotencyStore,
                new ObjectMapper(),
                eventPublisher,
                walletService,
                "app_test_id"
        );
    }

    @Test
    void refund_toWallet_creditsCustomerWalletAndDoesNotCallGateway() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_1", new BigDecimal("350.00"), BigDecimal.ZERO, "idem_1");
        payment.markCaptured("CF_ORDER_999");

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByPaymentIdAndStatus(payment.getId(), RefundStatus.INITIATED))
                .thenReturn(Collections.emptyList());
        when(orderPaymentPort.findByOrderId(orderId)).thenReturn(Optional.of(
                new OrderPaymentPort.PayableOrder(orderId, customerId, OrderStatus.CANCELLED, new BigDecimal("350.00"))
        ));

        RefundPaymentRequestDto request = new RefundPaymentRequestDto(new BigDecimal("350.00"), "Order cancelled", true);
        RefundInitiationResponseDto response = paymentService.refund(
                payment.getId(), request, PaymentServiceImpl.SYSTEM_ACTOR_ID, true);

        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        // Verify customer wallet was credited
        verify(walletService).credit(
                eq(OwnerType.CUSTOMER),
                eq(customerId),
                eq(new BigDecimal("350.00")),
                eq(LedgerReferenceType.REFUND),
                eq(payment.getId())
        );

        // Verify RefundProcessedEvent published
        verify(eventPublisher).publishEvent(any(RefundProcessedEvent.class));

        // Verify Cashfree gateway refund was NOT invoked
        verify(cashfreeClient, never()).createRefund(any(), any(), any());
    }

    @Test
    void refund_whenAmountExceedsTotalPaid_throwsBadRequestException() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_2", new BigDecimal("100.00"), new BigDecimal("50.00"), "idem_2");
        payment.markCaptured("CF_ORDER_100");

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        RefundPaymentRequestDto request = new RefundPaymentRequestDto(new BigDecimal("200.00"), "Over refund", true);

        assertThatThrownBy(() -> paymentService.refund(payment.getId(), request, null, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Refund amount cannot exceed captured payment amount");
    }

    @Test
    void refund_whenPaymentNotCaptured_throwsUnprocessableEntityException() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_3", new BigDecimal("100.00"), BigDecimal.ZERO, "idem_3");
        // Status is PENDING

        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        RefundPaymentRequestDto request = new RefundPaymentRequestDto(new BigDecimal("100.00"), "Cancel", true);

        assertThatThrownBy(() -> paymentService.refund(payment.getId(), request, null, true))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Payment is not refundable in its current status");
    }
}

