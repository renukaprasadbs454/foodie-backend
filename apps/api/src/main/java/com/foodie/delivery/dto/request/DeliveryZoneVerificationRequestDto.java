package com.foodie.delivery.dto.request;

import jakarta.validation.constraints.NotNull;

public record DeliveryZoneVerificationRequestDto(
        @NotNull(message = "latitude is required")
        Double latitude,

        @NotNull(message = "longitude is required")
        Double longitude,

        String address,

        String zoneId
) {
    public DeliveryZoneVerificationRequestDto(Double latitude, Double longitude, String address) {
        this(latitude, longitude, address, null);
    }
}

