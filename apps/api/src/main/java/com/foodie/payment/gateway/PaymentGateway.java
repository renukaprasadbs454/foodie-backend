package com.foodie.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {

    String getGatewayName();

    PaymentGatewayOrderResult createOrder(PaymentGatewayOrderCommand command);

    boolean verifyPaymentSignature(PaymentGatewayVerifyCommand command);

    record PaymentGatewayOrderCommand(
            UUID orderId,
            long amountPaise,
            BigDecimal amountInr,
            String currency,
            String receipt
    ) {
    }

    record PaymentGatewayOrderResult(
            String gatewayOrderId,
            long amountPaise,
            BigDecimal amountInr,
            String currency,
            String publicKey
    ) {
    }

    record PaymentGatewayVerifyCommand(
            String gatewayOrderId,
            String gatewayPaymentId,
            String signature
    ) {
    }
}
