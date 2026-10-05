package com.foodie.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponseDto(
        UUID id,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String status,
        String gatewayTransactionId,
        String gatewayName,
        Instant createdAt,
        Instant updatedAt
) {
}
