package com.foodie.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VerifyPaymentRequestDto(
        @NotNull(message = "orderId is required")
        UUID orderId,

        @NotBlank(message = "cashfreeOrderId is required")
        String cashfreeOrderId
) {
}
