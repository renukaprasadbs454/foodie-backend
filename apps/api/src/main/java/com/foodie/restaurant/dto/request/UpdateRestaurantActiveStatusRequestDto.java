package com.foodie.restaurant.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateRestaurantActiveStatusRequestDto(
        @NotNull Boolean active
) {
}