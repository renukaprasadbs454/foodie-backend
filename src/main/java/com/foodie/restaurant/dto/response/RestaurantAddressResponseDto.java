package com.foodie.restaurant.dto.response;

import java.math.BigDecimal;

public record RestaurantAddressResponseDto(
        String line1,
        String line2,
        String city,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
