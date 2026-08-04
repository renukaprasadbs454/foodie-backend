package com.foodie.payment.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
        payment.razorpayOrderId = razorpayOrderId;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    public void markCaptured(String razorpayPaymentId) {
        this.status = PaymentStatus.CAPTURED;
        this.razorpayPaymentId = razorpayPaymentId;
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

    /** Re-open a FAILED payment for a new Razorpay intent (unique order_id constraint). */
    public void reinitiate(String razorpayOrderId, String idempotencyKey, BigDecimal amount) {
        this.razorpayOrderId = razorpayOrderId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.razorpayPaymentId = null;
        this.capturedAt = null;
    }

    public UUID getOrderId() {
        return orderId;
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
