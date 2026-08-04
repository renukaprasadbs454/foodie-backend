package com.foodie.cart.mapper;

import com.foodie.cart.dto.response.CartItemResponseDto;
import com.foodie.cart.dto.response.CartResponseDto;
import com.foodie.cart.entity.Cart;
import com.foodie.cart.entity.CartItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {

    public CartResponseDto toCart(Cart cart, List<PricedLine> lines) {
        List<CartItemResponseDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (PricedLine line : lines) {
            items.add(line.dto());
            subtotal = subtotal.add(line.dto().lineTotal());
        }
        return new CartResponseDto(cart.getId(), cart.getRestaurantId(), items, subtotal);
    }

    public CartItemResponseDto toItem(CartItem item, BigDecimal unitPrice) {
        BigDecimal unit = unitPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new CartItemResponseDto(
                item.getId(),
                item.getMenuItemId(),
                item.getVariantId(),
                item.getQuantity(),
                item.getNotes(),
                unit,
                lineTotal
        );
    }

    public record PricedLine(CartItemResponseDto dto) {
    }
}
