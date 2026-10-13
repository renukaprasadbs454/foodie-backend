package com.foodie.admin.dto;

public record ActiveOperatingZoneDto(
        String id,
        String zoneName,
        String cityName,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String status,
        boolean restaurantEnabled,
        boolean deliveryPartnerEnabled,
        boolean customerOrderingEnabled,
        Integer activeDrivers
) {}
