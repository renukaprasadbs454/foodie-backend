package com.foodie.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOrderRequestDto(
        @NotNull UUID addressId,
        @Size(max = 30) String couponCode,
        String paymentMethod
) {
    public CreateOrderRequestDto(UUID addressId, String couponCode) {
        this(addressId, couponCode, null);
    }
}
