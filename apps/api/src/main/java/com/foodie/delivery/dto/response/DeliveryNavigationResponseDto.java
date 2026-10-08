package com.foodie.delivery.dto.response;

import java.util.UUID;

public record DeliveryNavigationResponseDto(
        UUID assignmentId,
        UUID orderId,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        String restaurantName,
        String restaurantPhone,
        String restaurantAddress,
        Double restaurantLat,
        Double restaurantLng
) {
}
