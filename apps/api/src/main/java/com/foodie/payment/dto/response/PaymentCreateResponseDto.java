package com.foodie.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreateResponseDto(
        UUID paymentId,
        String gateway,
        String gatewayOrderId,
        long amountPaise,
        BigDecimal amount,
        String currency,
        String keyId
) {
}
