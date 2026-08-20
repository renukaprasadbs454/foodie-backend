package com.foodie.review.dto.response;

import java.util.Map;

public record ReviewSummaryDto(
        double averageRating,
        long totalReviews,
        long positiveReviews,
        long needsImprovement,
        Map<String, Long> starCounts
) {
}
