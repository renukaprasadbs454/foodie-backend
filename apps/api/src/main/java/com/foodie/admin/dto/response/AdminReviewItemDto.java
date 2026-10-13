package com.foodie.admin.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AdminReviewItemDto(
        UUID id,
        UUID orderId,
        UUID restaurantId,
        String restaurantName,
        UUID customerId,
        String customerName,
        UUID deliveryPartnerId,
        String deliveryPartnerName,
        short restaurantRating,
        Short deliveryRating,
        int rating,
        String comment,
        Instant createdAt,
        Instant updatedAt,
        String status,
        boolean isReported
) {
}
