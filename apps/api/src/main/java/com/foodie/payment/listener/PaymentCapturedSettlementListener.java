package com.foodie.payment.listener;

import com.foodie.payment.repository.PaymentRepository;
import com.foodie.payment.service.SettlementService;
import com.foodie.shared.event.PaymentCapturedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentCapturedSettlementListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCapturedSettlementListener.class);

    private final PaymentRepository paymentRepository;
    private final SettlementService settlementService;

    public PaymentCapturedSettlementListener(
            PaymentRepository paymentRepository,
            SettlementService settlementService) {
        this.paymentRepository = paymentRepository;
        this.settlementService = settlementService;
    }

    @EventListener
    @Transactional
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        log.info("Processing settlement for captured payment on order {}", event.orderId());
        paymentRepository.findById(event.paymentId()).ifPresent(payment -> {
            try {
                settlementService.processPaymentSettlement(payment);
            } catch (Exception ex) {
                log.error("Error creating settlement for orderId={}: {}", event.orderId(), ex.getMessage(), ex);
            }
        });
    }
}
