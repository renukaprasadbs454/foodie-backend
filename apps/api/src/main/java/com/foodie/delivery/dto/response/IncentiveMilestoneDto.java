package com.foodie.delivery.dto.response;

import java.math.BigDecimal;

public record IncentiveMilestoneDto(
        int trips,
        BigDecimal rewardAmount,
        String status, // 'Completed', 'In Progress', 'Upcoming', 'Locked'
        int remaining,
        boolean isEarned
) {
}
