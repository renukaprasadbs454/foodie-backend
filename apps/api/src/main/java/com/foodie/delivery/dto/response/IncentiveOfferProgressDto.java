package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record IncentiveOfferProgressDto(
        String id,
        String title,
        String category,
        String description,
        int target,
        int currentProgress,
        BigDecimal rewardAmount,
        String unit,
        String status, // 'Upcoming', 'In Progress', 'Completed', 'Earned', 'Expired'
        Integer remaining,
        String validityPeriod,
        boolean isEarned,
        boolean active,
        List<IncentiveMilestoneDto> milestones,
        List<IncentiveConditionProgressDto> conditions
) {
    public IncentiveOfferProgressDto(
            String id,
            String title,
            String category,
            String description,
            int target,
            int currentProgress,
            BigDecimal rewardAmount,
            String unit,
            String status,
            Integer remaining,
            String validityPeriod,
            boolean isEarned,
            boolean active
    ) {
        this(id, title, category, description, target, currentProgress, rewardAmount, unit, status, remaining, validityPeriod, isEarned, active, null, null);
    }

    public IncentiveOfferProgressDto(
            String id,
            String title,
            String category,
            String description,
            int target,
            int currentProgress,
            BigDecimal rewardAmount,
            String unit,
            String status,
            Integer remaining,
            String validityPeriod,
            boolean isEarned,
            boolean active,
            List<IncentiveMilestoneDto> milestones
    ) {
        this(id, title, category, description, target, currentProgress, rewardAmount, unit, status, remaining, validityPeriod, isEarned, active, milestones, null);
    }
}
