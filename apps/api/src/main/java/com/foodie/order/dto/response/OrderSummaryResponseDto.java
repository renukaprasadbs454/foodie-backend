package com.foodie.order.dto.response;

import com.foodie.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummaryResponseDto(
        UUID orderId,
        String orderNumber,
        OrderStatus status,
        UUID restaurantId,
        BigDecimal totalAmount,
        Instant placedAt,
        Instant foodReadyAt,
        Instant assignmentScheduledAt,
        UUID deliveryPartnerId,
        String customerName,
        String customerPhone,
        String deliveryAddress
) {
        public OrderSummaryResponseDto(
                UUID orderId,
                String orderNumber,
                OrderStatus status,
                UUID restaurantId,
                BigDecimal totalAmount,
                Instant placedAt,
                Instant foodReadyAt,
                Instant assignmentScheduledAt,
                UUID deliveryPartnerId
        ) {
                this(orderId, orderNumber, status, restaurantId, totalAmount, placedAt, foodReadyAt, assignmentScheduledAt, deliveryPartnerId, null, null, null);
        }
}
