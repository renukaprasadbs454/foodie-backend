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
        String deliveryAddress,
        String paymentMethod,
        String paymentStatus,
        String transactionId
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
                this(orderId, orderNumber, status, restaurantId, totalAmount, placedAt, foodReadyAt, assignmentScheduledAt, deliveryPartnerId, null, null, null, null, null, null);
        }

        public OrderSummaryResponseDto(
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
                this(orderId, orderNumber, status, restaurantId, totalAmount, placedAt, foodReadyAt, assignmentScheduledAt, deliveryPartnerId, customerName, customerPhone, deliveryAddress, null, null, null);
        }
}
