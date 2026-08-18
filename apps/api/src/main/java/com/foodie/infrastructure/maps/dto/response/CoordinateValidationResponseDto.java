package com.foodie.infrastructure.maps.dto.response;

import java.math.BigDecimal;

public record CoordinateValidationResponseDto(
        boolean valid,
        BigDecimal latitude,
        BigDecimal longitude,
        String message
) {
}
