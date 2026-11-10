package com.foodie.realtime.ws;

import com.foodie.shared.contract.OrderNotificationQuery;
import com.foodie.shared.event.OrderPlacedEvent;
import com.foodie.shared.event.OrderStatusChangedEvent;
import com.foodie.shared.event.PaymentCapturedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderStatusBroadcastHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusBroadcastHandler.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderNotificationQuery orderNotificationQuery;

    public OrderStatusBroadcastHandler(SimpMessagingTemplate messagingTemplate, OrderNotificationQuery orderNotificationQuery) {
        this.messagingTemplate = messagingTemplate;
        this.orderNotificationQuery = orderNotificationQuery;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        String restaurantTopic = "/topic/restaurant/" + event.restaurantId() + "/orders";
        log.info("Broadcasting NEW_ORDER_ALERT {} to restaurant topic {}", event.orderId(), restaurantTopic);

        Map<String, Object> payload = Map.of(
                "type", "NEW_ORDER_ALERT",
                "orderId", event.orderId().toString(),
                "restaurantId", event.restaurantId().toString(),
                "timestamp", event.occurredAt().toString());

        messagingTemplate.convertAndSend(restaurantTopic, payload);
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        String destination = "/topic/order/" + event.orderId();
        log.debug("Broadcasting order status change {} to {}", event.toStatus(), destination);

        Map<String, Object> payload = Map.of(
                "type", "ORDER_STATUS_CHANGED",
                "orderId", event.orderId().toString(),
                "status", event.toStatus().name(),
                "timestamp", event.occurredAt().toString());

        messagingTemplate.convertAndSend(destination, payload);

        // Fan out to restaurant-specific orders topic as well
        orderNotificationQuery.findByOrderId(event.orderId()).ifPresent(snapshot -> {
            String restaurantTopic = "/topic/restaurant/" + snapshot.restaurantId() + "/orders";
            messagingTemplate.convertAndSend(restaurantTopic, payload);
        });
    }

    @EventListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        String destination = "/topic/order/" + event.orderId();
        log.info("Broadcasting PAYMENT_CAPTURED for order {} to {}", event.orderId(), destination);

        Map<String, Object> payload = Map.of(
                "type", "PAYMENT_CAPTURED",
                "orderId", event.orderId().toString(),
                "paymentStatus", "PAID",
                "paymentMethod", "ONLINE",
                "timestamp", event.occurredAt().toString());

        messagingTemplate.convertAndSend(destination, payload);

        // Fan out to restaurant
        orderNotificationQuery.findByOrderId(event.orderId()).ifPresent(snapshot -> {
            String restaurantTopic = "/topic/restaurant/" + snapshot.restaurantId() + "/orders";
            messagingTemplate.convertAndSend(restaurantTopic, payload);
        });

        // Fan out to delivery partner assignment topic
        messagingTemplate.convertAndSend("/topic/delivery/order/" + event.orderId(), payload);
    }
}

