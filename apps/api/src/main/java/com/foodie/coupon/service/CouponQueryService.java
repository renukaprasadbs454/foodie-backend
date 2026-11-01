package com.foodie.coupon.service;

import com.foodie.coupon.dto.request.ApplyCouponRequestDto;
import com.foodie.coupon.dto.response.ApplyCouponResponseDto;
import com.foodie.coupon.dto.response.EligibleCouponResponseDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Customer-facing coupon HTTP operations. */
public interface CouponQueryService {

    List<EligibleCouponResponseDto> listEligibleForCaller(
            UUID userCredentialId, UUID restaurantId, BigDecimal cartTotal);

    default List<EligibleCouponResponseDto> listEligibleForCaller(
            UUID userCredentialId, UUID restaurantId, BigDecimal cartTotal, String customerZoneId) {
        return listEligibleForCaller(userCredentialId, restaurantId, cartTotal);
    }

    ApplyCouponResponseDto applyForCaller(UUID userCredentialId, ApplyCouponRequestDto request);
}
