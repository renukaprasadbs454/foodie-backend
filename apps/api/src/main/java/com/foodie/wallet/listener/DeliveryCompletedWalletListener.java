package com.foodie.wallet.listener;

import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.delivery.service.DeliveryIncentiveService;
import com.foodie.shared.contract.OrderDeliveryFeeQuery;
import com.foodie.shared.event.DeliveryCompletedEvent;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver earnings: DeliveryCompletedEvent → append-only CREDIT on partner wallet + incentive evaluation.
 */
@Component
public class DeliveryCompletedWalletListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompletedWalletListener.class);

    private final WalletService walletService;
    private final OrderDeliveryFeeQuery orderDeliveryFeeQuery;
    private final DeliveryIncentiveService deliveryIncentiveService;

    public DeliveryCompletedWalletListener(
            WalletService walletService,
            OrderDeliveryFeeQuery orderDeliveryFeeQuery,
            DeliveryIncentiveService deliveryIncentiveService) {
        this.walletService = walletService;
        this.orderDeliveryFeeQuery = orderDeliveryFeeQuery;
        this.deliveryIncentiveService = deliveryIncentiveService;
    }

    @EventListener
    @Transactional
    public void onDeliveryCompleted(DeliveryCompletedEvent event) {
        BigDecimal fee = orderDeliveryFeeQuery.findDeliveryFeeByOrderId(event.orderId())
                .orElse(null);
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(
                    "Skipping base wallet credit for assignment {} — missing/invalid delivery fee on order {}",
                    event.assignmentId(),
                    event.orderId());
        } else {
            log.info(
                    "Crediting delivery partner {} for assignment {} amount {}",
                    event.deliveryPartnerId(),
                    event.assignmentId(),
                    fee);
            walletService.credit(
                    OwnerType.DELIVERY_PARTNER,
                    event.deliveryPartnerId(),
                    fee,
                    LedgerReferenceType.DELIVERY_ASSIGNMENT,
                    event.assignmentId());
        }

        // Evaluate active Admin incentive rules & credit wallet accordingly
        try {
            deliveryIncentiveService.processDeliveryCompletion(
                    event.deliveryPartnerId(),
                    event.orderId(),
                    event.assignmentId());
        } catch (Exception e) {
            log.error("Failed to process delivery incentive completion for partner {} assignment {}: {}",
                    event.deliveryPartnerId(), event.assignmentId(), e.getMessage(), e);
        }
    }
}
