package com.foodie.coupon.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record EligibleCouponResponseDto(
        String code,
        String discountType,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Instant expiryDate,
        String zoneId
) {
    public EligibleCouponResponseDto(
            String code,
            String discountType,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Instant expiryDate
    ) {
        this(code, discountType, value, minOrderAmount, maxDiscountAmount, expiryDate, null);
    }
}
