package com.foodie.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record IncentiveMilestoneDto(
        int trips,
        BigDecimal rewardAmount,
        String status, // 'Completed', 'In Progress', 'Upcoming', 'Locked'
        int remaining,
        boolean isEarned
) {
    @JsonProperty("orders")
    public int orders() {
        return trips;
    }

    @JsonProperty("reward")
    public BigDecimal reward() {
        return rewardAmount;
    }
}
