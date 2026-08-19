package com.foodie.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentRequestDto(
        @NotNull(message = "orderId is required.")
        UUID orderId
) {
}
