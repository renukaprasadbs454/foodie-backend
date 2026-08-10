package com.foodie.cart.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponseDto(
        UUID cartId,
        UUID restaurantId,
        List<CartItemResponseDto> items,
        BigDecimal subtotal
) {
}
