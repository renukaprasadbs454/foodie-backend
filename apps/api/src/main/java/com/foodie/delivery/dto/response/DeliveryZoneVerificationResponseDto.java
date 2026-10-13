package com.foodie.delivery.dto.response;

public record DeliveryZoneVerificationResponseDto(
        boolean verified,
        String zoneId,
        String zoneName,
        String cityName,
        Double latitude,
        Double longitude,
        String message
) {}
