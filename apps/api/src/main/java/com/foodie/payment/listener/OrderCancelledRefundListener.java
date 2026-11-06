package com.foodie.payment.listener;

import com.foodie.common.enums.PaymentStatus;
import com.foodie.payment.dto.request.RefundPaymentRequestDto;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.service.PaymentService;
import com.foodie.payment.service.impl.PaymentServiceImpl;
import com.foodie.shared.event.OrderCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-refund on OrderCancelledEvent when a captured payment exists (Phase3 §8.4 / §10.10).
 */
@Component
public class OrderCancelledRefundListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledRefundListener.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public OrderCancelledRefundListener(PaymentRepository paymentRepository, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @EventListener
    @Transactional
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentRepository.findByOrderId(event.orderId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() != PaymentStatus.CAPTURED) {
                log.info("Skip auto-refund for orderId={} paymentStatus={}",
                        event.orderId(), payment.getStatus());
                return;
            }
            java.math.BigDecimal totalPaid = payment.getAmount().add(
                    payment.getWalletAmount() != null ? payment.getWalletAmount() : java.math.BigDecimal.ZERO);
            if (totalPaid.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.info("Skip auto-refund for orderId={} as totalPaid is zero", event.orderId());
                return;
            }
            String reason = event.reason() == null || event.reason().isBlank()
                    ? "Order cancelled - Refund deposited to Delimo App Wallet"
                    : event.reason();
            log.info("Auto-refunding cancelled orderId={} totalPaid={} to customer wallet", event.orderId(), totalPaid);
            paymentService.refund(
                    payment.getId(),
                    new RefundPaymentRequestDto(totalPaid, reason, true),
                    PaymentServiceImpl.SYSTEM_ACTOR_ID,
                    true
            );
        }, () -> log.info("No payment row for cancelled orderId={}", event.orderId()));
    }
}
