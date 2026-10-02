package com.foodie.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "foodie.delivery")
public class DeliveryProperties {

    private double offerRadiusKm = 5.0;
    private int deliveryAssignmentLeadTimeMinutes = 10;

    public double getOfferRadiusKm() {
        return offerRadiusKm;
    }

    public void setOfferRadiusKm(double offerRadiusKm) {
        this.offerRadiusKm = offerRadiusKm;
    }

    public int getDeliveryAssignmentLeadTimeMinutes() {
        return deliveryAssignmentLeadTimeMinutes;
    }

    public void setDeliveryAssignmentLeadTimeMinutes(int deliveryAssignmentLeadTimeMinutes) {
        this.deliveryAssignmentLeadTimeMinutes = deliveryAssignmentLeadTimeMinutes;
    }
}

