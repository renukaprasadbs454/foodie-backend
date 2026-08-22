package com.foodie.restaurant.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestaurantDetailResponseDto(
        UUID restaurantId,
        String name,
        String description,
        List<String> cuisineTypes,
        RestaurantAddressResponseDto address,
        BigDecimal latitude,
        BigDecimal longitude,
        String logoImageUrl,
        String coverImageUrl,
        BigDecimal avgRating,
        String status,
        boolean active,
        boolean online,
        BigDecimal commissionPct,
        UUID ownerUserCredentialId
) {
}
