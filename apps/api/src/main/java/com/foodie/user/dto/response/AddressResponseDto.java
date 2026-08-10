package com.foodie.user.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AddressResponseDto(
        UUID addressId,
        String label,
        String line1,
        String line2,
        String city,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault
) {
}
