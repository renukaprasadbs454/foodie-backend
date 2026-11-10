package com.foodie.shared.contract;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface OrderRestaurantAmountQuery {

    Optional<RestaurantOrderAmount> findAmountByOrderId(UUID orderId);

    record RestaurantOrderAmount(
            UUID restaurantId,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            UUID couponId) {
        public RestaurantOrderAmount(UUID restaurantId, BigDecimal subtotal, BigDecimal taxAmount) {
            this(restaurantId, subtotal, taxAmount, BigDecimal.ZERO, null);
        }
    }
}
