package com.foodie.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateDepositOrderRequestDto(
        @NotNull(message = "Deposit amount is required")
        @Positive(message = "Deposit amount must be greater than zero")
        BigDecimal amount
) {
}
