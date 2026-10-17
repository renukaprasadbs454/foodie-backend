package com.foodie.order.dto.response;

import com.foodie.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDto(
                UUID orderId,
                String orderNumber,
                OrderStatus status,
                UUID customerId,
                UUID restaurantId,
                UUID addressId,
                BigDecimal subtotal,
                BigDecimal deliveryFee,
                BigDecimal discountAmount,
                BigDecimal taxAmount,
                BigDecimal totalAmount,
                Instant placedAt,
                Integer preparationTime,
                Instant foodReadyAt,
                Instant assignmentScheduledAt,
                UUID deliveryPartnerId,
                List<OrderItemResponseDto> items,
                List<OrderStatusEventResponseDto> orderStatusEvents,
                String customerName,
                String customerPhone,
                String deliveryAddress,
                String paymentMethod,
                String paymentStatus,
                String transactionId) {

        public OrderResponseDto(
                        UUID orderId,
                        String orderNumber,
                        OrderStatus status,
                        UUID customerId,
                        UUID restaurantId,
                        UUID addressId,
                        BigDecimal subtotal,
                        BigDecimal deliveryFee,
                        BigDecimal discountAmount,
                        BigDecimal taxAmount,
                        BigDecimal totalAmount,
                        Instant placedAt,
                        Integer preparationTime,
                        Instant foodReadyAt,
                        Instant assignmentScheduledAt,
                        UUID deliveryPartnerId,
                        List<OrderItemResponseDto> items,
                        List<OrderStatusEventResponseDto> orderStatusEvents) {
                this(orderId, orderNumber, status, customerId, restaurantId, addressId, subtotal, deliveryFee,
                                discountAmount, taxAmount, totalAmount, placedAt, preparationTime, foodReadyAt,
                                assignmentScheduledAt, deliveryPartnerId, items, orderStatusEvents, null, null, null, null, null, null);
        }

        public OrderResponseDto(
                        UUID orderId,
                        String orderNumber,
                        OrderStatus status,
                        UUID customerId,
                        UUID restaurantId,
                        UUID addressId,
                        BigDecimal subtotal,
                        BigDecimal deliveryFee,
                        BigDecimal discountAmount,
                        BigDecimal taxAmount,
                        BigDecimal totalAmount,
                        Instant placedAt,
                        Integer preparationTime,
                        Instant foodReadyAt,
                        Instant assignmentScheduledAt,
                        UUID deliveryPartnerId,
                        List<OrderItemResponseDto> items,
                        List<OrderStatusEventResponseDto> orderStatusEvents,
                        String customerName,
                        String customerPhone,
                        String deliveryAddress) {
                this(orderId, orderNumber, status, customerId, restaurantId, addressId, subtotal, deliveryFee,
                                discountAmount, taxAmount, totalAmount, placedAt, preparationTime, foodReadyAt,
                                assignmentScheduledAt, deliveryPartnerId, items, orderStatusEvents, customerName, customerPhone, deliveryAddress, null, null, null);
        }
}
