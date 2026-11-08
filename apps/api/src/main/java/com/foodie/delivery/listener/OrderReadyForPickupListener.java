package com.foodie.delivery.listener;

import com.foodie.common.enums.OrderStatus;
import com.foodie.delivery.service.DeliveryService;
import com.foodie.shared.contract.OrderDeliveryPort;
import com.foodie.shared.event.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderReadyForPickupListener {

    private static final Logger log = LoggerFactory.getLogger(OrderReadyForPickupListener.class);

    private final DeliveryService deliveryService;
    private final OrderDeliveryPort orderDeliveryPort;

    public OrderReadyForPickupListener(DeliveryService deliveryService, OrderDeliveryPort orderDeliveryPort) {
        this.deliveryService = deliveryService;
        this.orderDeliveryPort = orderDeliveryPort;
    }

    @EventListener
    @Transactional
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.toStatus() != OrderStatus.CONFIRMED &&
            event.toStatus() != OrderStatus.ACCEPTED &&
            event.toStatus() != OrderStatus.PREPARING &&
            event.toStatus() != OrderStatus.WAITING_FOR_DELIVERY_PARTNER &&
            event.toStatus() != OrderStatus.READY_FOR_PICKUP) {
            return;
        }

        log.info("Order {} transitioned to {} — triggering immediate delivery partner assignment", event.orderId(), event.toStatus());
        deliveryService.createAssignmentForOrder(event.orderId());
    }
}
