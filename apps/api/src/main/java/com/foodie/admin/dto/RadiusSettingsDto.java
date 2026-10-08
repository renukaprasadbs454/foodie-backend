package com.foodie.admin.dto;

import java.math.BigDecimal;

public class RadiusSettingsDto {

    private BigDecimal maxDeliveryRadius;
    private BigDecimal customerSearchRadius;
    private BigDecimal driverDispatchRadius;
    private String distanceCalculationMode; // GPS_ROAD, HAVERSINE

    public RadiusSettingsDto() {}

    public RadiusSettingsDto(BigDecimal maxDeliveryRadius, BigDecimal customerSearchRadius, BigDecimal driverDispatchRadius, String distanceCalculationMode) {
        this.maxDeliveryRadius = maxDeliveryRadius;
        this.customerSearchRadius = customerSearchRadius;
        this.driverDispatchRadius = driverDispatchRadius;
        this.distanceCalculationMode = distanceCalculationMode;
    }

    public BigDecimal getMaxDeliveryRadius() {
        return maxDeliveryRadius;
    }

    public void setMaxDeliveryRadius(BigDecimal maxDeliveryRadius) {
        this.maxDeliveryRadius = maxDeliveryRadius;
    }

    public BigDecimal getCustomerSearchRadius() {
        return customerSearchRadius;
    }

    public void setCustomerSearchRadius(BigDecimal customerSearchRadius) {
        this.customerSearchRadius = customerSearchRadius;
    }

    public BigDecimal getDriverDispatchRadius() {
        return driverDispatchRadius;
    }

    public void setDriverDispatchRadius(BigDecimal driverDispatchRadius) {
        this.driverDispatchRadius = driverDispatchRadius;
    }

    public String getDistanceCalculationMode() {
        return distanceCalculationMode;
    }

    public void setDistanceCalculationMode(String distanceCalculationMode) {
        this.distanceCalculationMode = distanceCalculationMode;
    }
}
