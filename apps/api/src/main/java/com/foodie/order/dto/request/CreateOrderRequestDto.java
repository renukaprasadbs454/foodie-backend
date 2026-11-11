package com.foodie.order.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequestDto(
        @NotNull UUID addressId,
        @Size(max = 30) String couponCode,
        String paymentMethod,
        BigDecimal totalAmount,
        BigDecimal deliveryFee,
        BigDecimal taxAmount
) {
    public CreateOrderRequestDto(UUID addressId, String couponCode) {
        this(addressId, couponCode, null, null, null, null);
    }

    public CreateOrderRequestDto(UUID addressId, String couponCode, String paymentMethod) {
        this(addressId, couponCode, paymentMethod, null, null, null);
    }
}
