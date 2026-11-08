package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CodPaymentVerificationResponseDto(
        UUID orderId,
        UUID assignmentId,
        String cfOrderId,
        boolean verified,
        String paymentStatus,
        BigDecimal amount,
        String message
) {}

