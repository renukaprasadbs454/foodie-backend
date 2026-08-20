package com.foodie.review.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RestaurantReviewItemDto(
        UUID reviewId,
        UUID id,
        String customerName,
        boolean verified,
        String orderNumber,
        String orderInfo,
        List<String> orderedItems,
        String itemInfo,
        int restaurantRating,
        Integer deliveryRating,
        String comment,
        Instant createdAt
) {
}
