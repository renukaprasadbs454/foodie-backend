package com.foodie.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_pricing_config")
public class DeliveryPricingConfig {

    @Id
    private UUID id;

    @Column(name = "min_price_per_delivery", nullable = false, precision = 10, scale = 2)
    private BigDecimal minPricePerDelivery;

    @Column(name = "money_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal moneyPerKm;

    @Column(name = "pricing_basis", nullable = false, length = 20)
    private String pricingBasis = "UNIVERSAL";

    @Column(name = "config_data", columnDefinition = "TEXT")
    private String configData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected DeliveryPricingConfig() {}

    public DeliveryPricingConfig(
            UUID id,
            BigDecimal minPricePerDelivery,
            BigDecimal moneyPerKm,
            String pricingBasis,
            String configData,
            Instant createdAt,
            Instant updatedAt,
            UUID updatedBy
    ) {
        this.id = id;
        this.minPricePerDelivery = minPricePerDelivery;
        this.moneyPerKm = moneyPerKm;
        this.pricingBasis = (pricingBasis != null && !pricingBasis.isBlank()) ? pricingBasis : "UNIVERSAL";
        this.configData = configData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public static DeliveryPricingConfig createDefault() {
        return new DeliveryPricingConfig(
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                new BigDecimal("200.00"),
                new BigDecimal("25.00"),
                "UNIVERSAL",
                null,
                Instant.now(),
                Instant.now(),
                null
        );
    }

    public void update(
            BigDecimal minPricePerDelivery,
            BigDecimal moneyPerKm,
            String pricingBasis,
            String configData,
            UUID updatedBy
    ) {
        this.minPricePerDelivery = minPricePerDelivery;
        this.moneyPerKm = moneyPerKm;
        if (pricingBasis != null && !pricingBasis.isBlank()) {
            this.pricingBasis = pricingBasis;
        }
        if (configData != null) {
            this.configData = configData;
        }
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getMinPricePerDelivery() {
        return minPricePerDelivery;
    }

    public BigDecimal getMoneyPerKm() {
        return moneyPerKm;
    }

    public String getPricingBasis() {
        return pricingBasis;
    }

    public String getConfigData() {
        return configData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
