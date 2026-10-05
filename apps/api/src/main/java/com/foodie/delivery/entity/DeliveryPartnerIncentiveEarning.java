package com.foodie.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "delivery_partner_incentive_earning")
public class DeliveryPartnerIncentiveEarning {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_partner_id", nullable = false, updatable = false)
    private DeliveryPartner deliveryPartner;

    @Column(name = "rule_id", nullable = false, length = 100, updatable = false)
    private String ruleId;

    @Column(name = "rule_title", nullable = false, length = 200, updatable = false)
    private String ruleTitle;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "reference_type", nullable = false, length = 50, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "period_date", nullable = false, updatable = false)
    private LocalDate periodDate;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    protected DeliveryPartnerIncentiveEarning() {}

    public static DeliveryPartnerIncentiveEarning create(
            DeliveryPartner deliveryPartner,
            String ruleId,
            String ruleTitle,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            UUID orderId,
            LocalDate periodDate
    ) {
        DeliveryPartnerIncentiveEarning earning = new DeliveryPartnerIncentiveEarning();
        earning.deliveryPartner = deliveryPartner;
        earning.ruleId = ruleId;
        earning.ruleTitle = ruleTitle;
        earning.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        earning.referenceType = referenceType;
        earning.referenceId = referenceId;
        earning.orderId = orderId;
        earning.periodDate = periodDate != null ? periodDate : LocalDate.now();
        earning.earnedAt = Instant.now();
        return earning;
    }

    @PrePersist
    void onCreate() {
        if (earnedAt == null) {
            earnedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public DeliveryPartner getDeliveryPartner() {
        return deliveryPartner;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getRuleTitle() {
        return ruleTitle;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public LocalDate getPeriodDate() {
        return periodDate;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }
}
