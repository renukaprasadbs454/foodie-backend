package com.foodie.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.common.enums.PaymentStatus;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.entity.Payment;
import com.foodie.payment.listener.OrderCancelledRefundListener;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.service.PaymentService;
import com.foodie.payment.service.impl.PaymentServiceImpl;
import com.foodie.shared.event.OrderCancelledEvent;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderCancelledRefundListenerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    private OrderCancelledRefundListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderCancelledRefundListener(paymentRepository, paymentService);
    }

    @Test
    void onOrderCancelled_whenPaymentCapturedOnline_refundsFullAmountToAppWallet() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_123", new BigDecimal("250.00"), BigDecimal.ZERO, "idem_1");
        payment.markCaptured("CF_ORDER_123");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        listener.onOrderCancelled(OrderCancelledEvent.of(orderId, "Customer cancelled"));

        ArgumentCaptor<RefundPaymentRequestDto> captor = ArgumentCaptor.forClass(RefundPaymentRequestDto.class);
        verify(paymentService).refund(eq(payment.getId()), captor.capture(), eq(PaymentServiceImpl.SYSTEM_ACTOR_ID), eq(true));

        RefundPaymentRequestDto dto = captor.getValue();
        assertThat(dto.amount()).isEqualByComparingTo("250.00");
        assertThat(dto.isRefundToWallet()).isTrue();
    }

    @Test
    void onOrderCancelled_whenPaymentCapturedWithWalletAndGateway_refundsCombinedTotalToAppWallet() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_123", new BigDecimal("150.00"), new BigDecimal("100.00"), "idem_2");
        payment.markCaptured("CF_ORDER_456");

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        listener.onOrderCancelled(OrderCancelledEvent.of(orderId, "Customer cancelled"));

        ArgumentCaptor<RefundPaymentRequestDto> captor = ArgumentCaptor.forClass(RefundPaymentRequestDto.class);
        verify(paymentService).refund(eq(payment.getId()), captor.capture(), eq(PaymentServiceImpl.SYSTEM_ACTOR_ID), eq(true));

        RefundPaymentRequestDto dto = captor.getValue();
        // 150 gateway + 100 wallet = 250 total
        assertThat(dto.amount()).isEqualByComparingTo("250.00");
        assertThat(dto.isRefundToWallet()).isTrue();
    }

    @Test
    void onOrderCancelled_whenPaymentPending_skipsRefund() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.initiate(orderId, "sess_123", new BigDecimal("200.00"), BigDecimal.ZERO, "idem_3");
        // Status is PENDING

        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        listener.onOrderCancelled(OrderCancelledEvent.of(orderId, "Customer cancelled"));

        verify(paymentService, never()).refund(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void onOrderCancelled_whenNoPaymentFound_skipsRefund() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        listener.onOrderCancelled(OrderCancelledEvent.of(orderId, "Customer cancelled"));

        verify(paymentService, never()).refund(any(), any(), any(), any(Boolean.class));
    }
}

