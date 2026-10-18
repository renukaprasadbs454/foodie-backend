package com.foodie.coupon.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponseDto(
                UUID couponId,
                String code,
                String discountType,
                String funderType,
                String couponType,
                String benefitMode,
                String approvalStatus,
                BigDecimal value,
                BigDecimal minOrderAmount,
                BigDecimal maxDiscountAmount,
                Instant expiryDate,
                Integer usageLimitTotal,
                int usageLimitPerUser,
                UUID restaurantId,
                boolean isActive,
                String zoneId) {

        public CouponResponseDto(
                UUID couponId,
                String code,
                String discountType,
                String funderType,
                String couponType,
                String benefitMode,
                String approvalStatus,
                BigDecimal value,
                BigDecimal minOrderAmount,
                BigDecimal maxDiscountAmount,
                Instant expiryDate,
                Integer usageLimitTotal,
                int usageLimitPerUser,
                UUID restaurantId,
                boolean isActive) {
                this(couponId, code, discountType, funderType, couponType, benefitMode, approvalStatus,
                        value, minOrderAmount, maxDiscountAmount, expiryDate, usageLimitTotal,
                        usageLimitPerUser, restaurantId, isActive, null);
        }
}
