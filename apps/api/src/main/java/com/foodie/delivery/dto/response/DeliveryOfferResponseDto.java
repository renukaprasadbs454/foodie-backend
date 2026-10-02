package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record DeliveryOfferResponseDto(
        UUID assignmentId,
        UUID orderId,
        String orderNumber,
        String restaurantName,
        String pickupAddress,
        String deliveryAddress,
        java.time.Instant expectedFoodReadyTime,
        Double estimatedDistance,
        BigDecimal estimatedFee
) {
}
