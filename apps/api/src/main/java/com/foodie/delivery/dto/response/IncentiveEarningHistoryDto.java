package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IncentiveEarningHistoryDto(
        UUID id,
        String ruleId,
        String ruleTitle,
        BigDecimal amount,
        String referenceType,
        UUID referenceId,
        UUID orderId,
        LocalDate periodDate,
        Instant earnedAt
) {
}
