package com.foodie.payment.gateway;

import com.foodie.infrastructure.razorpay.RazorpayClient;
import com.foodie.infrastructure.razorpay.RazorpayProperties;
import com.foodie.infrastructure.razorpay.RazorpaySignatureVerifier;
import org.springframework.stereotype.Component;

@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final RazorpaySignatureVerifier signatureVerifier;

    public RazorpayPaymentGateway(
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties,
            RazorpaySignatureVerifier signatureVerifier
    ) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.signatureVerifier = signatureVerifier;
    }

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    @Override
    public PaymentGatewayOrderResult createOrder(PaymentGatewayOrderCommand command) {
        var created = razorpayClient.createOrder(
                command.amountInr(),
                command.receipt(),
                command.orderId().toString()
        );
        return new PaymentGatewayOrderResult(
                created.razorpayOrderId(),
                command.amountPaise(),
                created.amountInr(),
                created.currency(),
                razorpayProperties.getKeyId()
        );
    }

    @Override
    public boolean verifyPaymentSignature(PaymentGatewayVerifyCommand command) {
        return signatureVerifier.isValidPaymentSignature(
                command.gatewayOrderId(),
                command.gatewayPaymentId(),
                command.signature()
        );
    }
}
