package com.foodie.delivery.dto.response;

import java.math.BigDecimal;

public record IncentiveOfferProgressDto(
        String id,
        String title,
        String category,
        String description,
        int target,
        int currentProgress,
        BigDecimal rewardAmount,
        String unit,
        String status, // 'UPCOMING', 'IN_PROGRESS', 'COMPLETED', 'EARNED', 'EXPIRED'
        Integer remaining,
        String validityPeriod,
        boolean isEarned,
        boolean active
) {
}
