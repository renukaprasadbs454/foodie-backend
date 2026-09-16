package com.foodie.payment.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_settlement")
public class OrderSettlement extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "delivery_partner_id")
    private UUID deliveryPartnerId;

    @Column(name = "total_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "food_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal foodSubtotal;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "restaurant_commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal restaurantCommissionRate;

    @Column(name = "restaurant_commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal restaurantCommissionAmount;

    @Column(name = "restaurant_payout", nullable = false, precision = 10, scale = 2)
    private BigDecimal restaurantPayout;

    @Column(name = "delivery_commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal deliveryCommissionRate;

    @Column(name = "delivery_commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryCommissionAmount;

    @Column(name = "delivery_payout", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryPayout;

    @Column(name = "admin_total_earnings", nullable = false, precision = 10, scale = 2)
    private BigDecimal adminTotalEarnings;

    @Column(name = "settlement_status", nullable = false, length = 30)
    private String settlementStatus;

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus;

    @Column(name = "distribution_status", nullable = false, length = 30)
    private String distributionStatus;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    protected OrderSettlement() {
    }

    public static OrderSettlement create(
            UUID orderId,
            UUID paymentId,
            UUID customerId,
            UUID restaurantId,
            UUID deliveryPartnerId,
            BigDecimal totalPaid,
            BigDecimal foodSubtotal,
            BigDecimal deliveryFee,
            BigDecimal platformFee,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal restaurantCommissionRate,
            BigDecimal restaurantCommissionAmount,
            BigDecimal restaurantPayout,
            BigDecimal deliveryCommissionRate,
            BigDecimal deliveryCommissionAmount,
            BigDecimal deliveryPayout,
            BigDecimal adminTotalEarnings,
            String transactionReference
    ) {
        OrderSettlement settlement = new OrderSettlement();
        settlement.orderId = orderId;
        settlement.paymentId = paymentId;
        settlement.customerId = customerId;
        settlement.restaurantId = restaurantId;
        settlement.deliveryPartnerId = deliveryPartnerId;
        settlement.totalPaid = totalPaid;
        settlement.foodSubtotal = foodSubtotal;
        settlement.deliveryFee = deliveryFee;
        settlement.platformFee = platformFee != null ? platformFee : new BigDecimal("40.00");
        settlement.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        settlement.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        settlement.restaurantCommissionRate = restaurantCommissionRate;
        settlement.restaurantCommissionAmount = restaurantCommissionAmount;
        settlement.restaurantPayout = restaurantPayout;
        settlement.deliveryCommissionRate = deliveryCommissionRate;
        settlement.deliveryCommissionAmount = deliveryCommissionAmount;
        settlement.deliveryPayout = deliveryPayout;
        settlement.adminTotalEarnings = adminTotalEarnings;
        settlement.settlementStatus = "SETTLED";
        settlement.paymentStatus = "CAPTURED";
        settlement.distributionStatus = "DISTRIBUTED";
        settlement.transactionReference = transactionReference;
        settlement.settledAt = Instant.now();
        return settlement;
    }

    public void markRefunded() {
        this.settlementStatus = "REFUNDED";
        this.paymentStatus = "REFUNDED";
        this.distributionStatus = "REVERSED";
    }

    public UUID getOrderId() { return orderId; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getDeliveryPartnerId() { return deliveryPartnerId; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public BigDecimal getFoodSubtotal() { return foodSubtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getPlatformFee() { return platformFee; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getRestaurantCommissionRate() { return restaurantCommissionRate; }
    public BigDecimal getRestaurantCommissionAmount() { return restaurantCommissionAmount; }
    public BigDecimal getRestaurantPayout() { return restaurantPayout; }
    public BigDecimal getDeliveryCommissionRate() { return deliveryCommissionRate; }
    public BigDecimal getDeliveryCommissionAmount() { return deliveryCommissionAmount; }
    public BigDecimal getDeliveryPayout() { return deliveryPayout; }
    public BigDecimal getAdminTotalEarnings() { return adminTotalEarnings; }
    public String getSettlementStatus() { return settlementStatus; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getDistributionStatus() { return distributionStatus; }
    public String getTransactionReference() { return transactionReference; }
    public Instant getSettledAt() { return settledAt; }
}
