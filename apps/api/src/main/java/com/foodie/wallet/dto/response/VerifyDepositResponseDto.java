package com.foodie.wallet.dto.response;

import java.math.BigDecimal;

public record VerifyDepositResponseDto(
        boolean success,
        String message,
        String paymentId,
        BigDecimal updatedBalance
) {
}
