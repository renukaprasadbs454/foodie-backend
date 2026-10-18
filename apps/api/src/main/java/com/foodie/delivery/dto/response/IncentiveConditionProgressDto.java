package com.foodie.delivery.dto.response;

public record IncentiveConditionProgressDto(
        String id,
        String type,
        String title,
        String value,
        String subtext,
        String status, // 'SATISFIED', 'PENDING', 'FAILED'
        double progressPercentage,
        String currentProgressText
) {
}
