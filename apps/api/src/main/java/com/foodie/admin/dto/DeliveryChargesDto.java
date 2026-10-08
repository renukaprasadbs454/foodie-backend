package com.foodie.admin.dto;

import java.math.BigDecimal;

public class DeliveryChargesDto {

    private BigDecimal baseCharge;
    private BigDecimal baseDistanceKm;
    private BigDecimal additionalChargePerKm;
    private BigDecimal freeDeliveryMinOrder;
    private BigDecimal nightSurcharge;
    private BigDecimal surgeMultiplier;

    public DeliveryChargesDto() {}

    public DeliveryChargesDto(BigDecimal baseCharge, BigDecimal baseDistanceKm, BigDecimal additionalChargePerKm, BigDecimal freeDeliveryMinOrder, BigDecimal nightSurcharge, BigDecimal surgeMultiplier) {
        this.baseCharge = baseCharge;
        this.baseDistanceKm = baseDistanceKm;
        this.additionalChargePerKm = additionalChargePerKm;
        this.freeDeliveryMinOrder = freeDeliveryMinOrder;
        this.nightSurcharge = nightSurcharge;
        this.surgeMultiplier = surgeMultiplier;
    }

    public BigDecimal getBaseCharge() {
        return baseCharge;
    }

    public void setBaseCharge(BigDecimal baseCharge) {
        this.baseCharge = baseCharge;
    }

    public BigDecimal getBaseDistanceKm() {
        return baseDistanceKm;
    }

    public void setBaseDistanceKm(BigDecimal baseDistanceKm) {
        this.baseDistanceKm = baseDistanceKm;
    }

    public BigDecimal getAdditionalChargePerKm() {
        return additionalChargePerKm;
    }

    public void setAdditionalChargePerKm(BigDecimal additionalChargePerKm) {
        this.additionalChargePerKm = additionalChargePerKm;
    }

    public BigDecimal getFreeDeliveryMinOrder() {
        return freeDeliveryMinOrder;
    }

    public void setFreeDeliveryMinOrder(BigDecimal freeDeliveryMinOrder) {
        this.freeDeliveryMinOrder = freeDeliveryMinOrder;
    }

    public BigDecimal getNightSurcharge() {
        return nightSurcharge;
    }

    public void setNightSurcharge(BigDecimal nightSurcharge) {
        this.nightSurcharge = nightSurcharge;
    }

    public BigDecimal getSurgeMultiplier() {
        return surgeMultiplier;
    }

    public void setSurgeMultiplier(BigDecimal surgeMultiplier) {
        this.surgeMultiplier = surgeMultiplier;
    }
}
