package com.foodie.delivery.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeliveryPartnerReviewsResponseDto(
        double averageRating,
        int totalReviews,
        int positivePercentage,
        Map<String, Integer> ratingBreakdown,
        List<ComplimentCountDto> compliments,
        List<DeliveryReviewItemDto> reviews
) {
    public record ComplimentCountDto(String label, int count, String icon) {}

    public record DeliveryReviewItemDto(
            UUID id,
            String customerName,
            int rating,
            String comment,
            String orderNumber,
            String timeAgo,
            List<String> tags
    ) {}
}
