package com.foodie.restaurant.dto.response;

import java.util.List;

public record CustomerZoneDetectResponseDto(
        boolean serviceable,
        String zoneId,
        String zoneName,
        String cityName,
        Double latitude,
        Double longitude,
        long restaurantCount,
        List<String> availableZones,
        String message
) {}
