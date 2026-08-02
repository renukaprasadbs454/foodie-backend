package com.foodie.menu.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateMenuItemRequestDto(
        @NotNull
        UUID categoryId,

        @NotBlank
        @Size(min = 2, max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        @Digits(integer = 8, fraction = 2)
        BigDecimal basePrice,

        @NotNull
        Boolean isVeg
) {
}
