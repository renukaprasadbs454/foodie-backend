package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CodPaymentInitiationResponseDto(
        UUID orderId,
        UUID assignmentId,
        String paymentSessionId,
        String cfOrderId,
        BigDecimal amount,
        String upiQrPayload,
        String qrBase64
) {
    public CodPaymentInitiationResponseDto(
            UUID orderId,
            UUID assignmentId,
            String paymentSessionId,
            String cfOrderId,
            BigDecimal amount,
            String upiQrPayload) {
        this(orderId, assignmentId, paymentSessionId, cfOrderId, amount, upiQrPayload, null);
    }
}
