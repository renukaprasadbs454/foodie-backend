package com.foodie.review.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** Public list item with customer summary. */
public record RestaurantReviewItemDto(
        UUID id,
        UUID orderId,
        UUID customerId,
        String customerName,
        int restaurantRating,
        Integer deliveryRating,
        String comment,
        Instant createdAt
) {
    public RestaurantReviewItemDto(
            int restaurantRating,
            Integer deliveryRating,
            String comment,
            Instant createdAt
    ) {
        this(null, null, null, "Verified Customer", restaurantRating, deliveryRating, comment, createdAt);
    }

    @JsonProperty("rating")
    public int rating() {
        return restaurantRating;
    }

    @JsonProperty("date")
    public Instant date() {
        return createdAt;
    }
}
