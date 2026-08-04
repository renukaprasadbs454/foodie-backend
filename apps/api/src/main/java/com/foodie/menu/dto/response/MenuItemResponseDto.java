package com.foodie.menu.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemResponseDto(
        UUID menuItemId,
        UUID categoryId,
        String name,
        String description,
        BigDecimal basePrice,
        boolean isVeg,
        boolean isAvailable,
        String imageUrl
) {
}
