package com.foodie.restaurant.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementStatementResponseDto(
        UUID restaurantId,
        String restaurantName,
        Instant fromDate,
        Instant toDate,
        long totalOrders,
        long deliveredOrders,
        long cancelledOrders,
        BigDecimal grossOrderValue,
        BigDecimal totalDiscounts,
        BigDecimal totalTaxes,
        BigDecimal totalPackagingFees,
        BigDecimal platformCommission,
        BigDecimal refundsDeducted,
        BigDecimal totalPayouts,
        BigDecimal netEarnings
) {
}
