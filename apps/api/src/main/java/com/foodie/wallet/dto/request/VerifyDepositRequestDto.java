package com.foodie.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record VerifyDepositRequestDto(
        @NotBlank(message = "Razorpay Order ID is required")
        String razorpayOrderId,

        @NotBlank(message = "Razorpay Payment ID is required")
        String razorpayPaymentId,

        String razorpaySignature,

        @NotNull(message = "Deposit amount is required")
        @Positive(message = "Deposit amount must be greater than zero")
        BigDecimal amount
) {
}
