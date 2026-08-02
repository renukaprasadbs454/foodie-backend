package com.foodie.restaurant.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RestaurantAddressRequestDto(
        @NotBlank
        @Size(max = 255)
        String line1,

        @Size(max = 255)
        String line2,

        @NotBlank
        @Size(max = 100)
        String city,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "pincode must be 6 digits")
        String pincode,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        BigDecimal latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        BigDecimal longitude
) {
}
