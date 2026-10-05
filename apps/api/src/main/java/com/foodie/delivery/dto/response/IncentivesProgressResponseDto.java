package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IncentivesProgressResponseDto(
        LocalDate date,
        long tripsCompleted,
        BigDecimal incentivesEarned,
        List<IncentiveOfferProgressDto> offers
) {
}
