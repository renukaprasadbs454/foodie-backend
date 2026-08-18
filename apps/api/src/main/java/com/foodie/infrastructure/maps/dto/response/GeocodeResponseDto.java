package com.foodie.infrastructure.maps.dto.response;

import java.math.BigDecimal;

public record GeocodeResponseDto(
        BigDecimal latitude,
        BigDecimal longitude,
        String formattedAddress,
        String city,
        String state,
        String country,
        String postalCode,
        String locality
) {
}
