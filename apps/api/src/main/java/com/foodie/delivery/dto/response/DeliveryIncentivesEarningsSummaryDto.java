package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryIncentivesEarningsSummaryDto(
        BigDecimal regularDeliveryEarnings,
        BigDecimal incentiveEarnings,
        BigDecimal earnedIncentives,
        BigDecimal pendingIncentives,
        BigDecimal paidOutIncentives,
        BigDecimal availableBalance,
        List<IncentiveEarningHistoryDto> recentIncentiveTransactions
) {
}
