package com.foodie.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.PaymentStatus;
import com.foodie.payment.state.PaymentStateMachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "razorpay_order_id", nullable = false, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "gateway", nullable = false, length = 50)
    private String gateway = "RAZORPAY";

    @Column(name = "amount_paise")
    private Long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "gateway_fee_paise")
    private Long gatewayFeePaise = 0L;

    @Column(name = "amount_refunded_paise")
    private Long amountRefundedPaise = 0L;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "captured_at")
    private Instant capturedAt;

    protected Payment() {
    }

    public static Payment initiate(
            UUID orderId,
            String razorpayOrderId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.gateway = "RAZORPAY";
        payment.razorpayOrderId = razorpayOrderId;
        payment.amount = amount;
        payment.amountPaise = amount != null ? amount.movePointRight(2).longValueExact() : 0L;
        payment.currency = "INR";
        payment.status = PaymentStatus.PENDING;
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    public static Payment initiate(
            UUID orderId,
            String gateway,
            String razorpayOrderId,
            BigDecimal amount,
            long amountPaise,
            String currency,
            String idempotencyKey
    ) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.gateway = gateway != null ? gateway : "RAZORPAY";
        payment.razorpayOrderId = razorpayOrderId;
        payment.amount = amount;
        payment.amountPaise = amountPaise;
        payment.currency = currency != null ? currency : "INR";
        payment.status = PaymentStatus.PENDING;
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    public void markCaptured(String razorpayPaymentId) {
        this.status = PaymentStatus.CAPTURED;
        this.razorpayPaymentId = razorpayPaymentId;
        this.capturedAt = Instant.now();
    }

    public void markCaptured(String razorpayPaymentId, String paymentMethod) {
        this.status = PaymentStatus.CAPTURED;
        this.razorpayPaymentId = razorpayPaymentId;
        this.paymentMethod = paymentMethod;
        this.capturedAt = Instant.now();
    }

    public void markFailed(String razorpayPaymentId) {
        this.status = PaymentStatus.FAILED;
        if (razorpayPaymentId != null) {
            this.razorpayPaymentId = razorpayPaymentId;
        }
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    public void transitionTo(PaymentStatus status) {
          PaymentStateMachine.validateTransition(this.status, status);
           this.status = status;
    }

    /** Re-open a FAILED payment for a new Razorpay intent (unique order_id constraint). */
    public void reinitiate(String razorpayOrderId, String idempotencyKey, BigDecimal amount) {
        this.razorpayOrderId = razorpayOrderId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.amountPaise = amount != null ? amount.movePointRight(2).longValueExact() : 0L;
        this.status = PaymentStatus.PENDING;
        this.razorpayPaymentId = null;
        this.capturedAt = null;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getGateway() {
        return gateway;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getAmountPaise() {
        return amountPaise != null ? amountPaise : (amount != null ? amount.movePointRight(2).longValueExact() : 0L);
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Long getGatewayFeePaise() {
        return gatewayFeePaise;
    }

    public Long getAmountRefundedPaise() {
        return amountRefundedPaise;
    }

    public String getMetadata() {
        return metadata;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }
}
