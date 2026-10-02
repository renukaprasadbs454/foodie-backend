
package com.foodie.admin.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "location_zones")
public class LocationZone {
    @Id
    private String id;
    private String zoneName;
    private String cityName;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
    private String polygonCoordinates;
    private Integer activeDrivers;
    private BigDecimal surgeMultiplier;
    private String status;
    private boolean restaurantEnabled;
    private boolean deliveryPartnerEnabled;
    private boolean customerOrderingEnabled;

    public LocationZone() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }
    public String getPolygonCoordinates() { return polygonCoordinates; }
    public void setPolygonCoordinates(String polygonCoordinates) { this.polygonCoordinates = polygonCoordinates; }
    public Integer getActiveDrivers() { return activeDrivers; }
    public void setActiveDrivers(Integer activeDrivers) { this.activeDrivers = activeDrivers; }
    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isRestaurantEnabled() { return restaurantEnabled; }
    public void setRestaurantEnabled(boolean restaurantEnabled) { this.restaurantEnabled = restaurantEnabled; }
    public boolean isDeliveryPartnerEnabled() { return deliveryPartnerEnabled; }
    public void setDeliveryPartnerEnabled(boolean deliveryPartnerEnabled) { this.deliveryPartnerEnabled = deliveryPartnerEnabled; }
    public boolean isCustomerOrderingEnabled() { return customerOrderingEnabled; }
    public void setCustomerOrderingEnabled(boolean customerOrderingEnabled) { this.customerOrderingEnabled = customerOrderingEnabled; }
}
