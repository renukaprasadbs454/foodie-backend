package com.foodie.restaurant.dto.request;

import com.foodie.common.enums.CuisineType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateRestaurantRequestDto(
        @NotBlank
        @Size(min = 2, max = 255)
        String name,

        @Size(max = 1000)
        String description,

        @NotEmpty
        List<@NotNull CuisineType> cuisineTypes,

        @NotNull
        @Valid
        RestaurantAddressRequestDto address,

        /**
         * Accepted for OpenAPI completeness only — ignored server-side (API Contracts §3.3).
         */
        BigDecimal commissionPct
) {
}
