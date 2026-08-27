package com.foodie.wallet.dto.response;

import java.math.BigDecimal;

public record DepositOrderResponseDto(
        String razorpayOrderId,
        BigDecimal amount,
        String currency,
        String keyId
) {
}
