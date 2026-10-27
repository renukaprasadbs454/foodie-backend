package com.foodie.admin.dto;

import java.time.Instant;
import java.util.List;

public class UnserviceableRequestDto {

    private String id;
    private String deliveryPartnerId;
    private String restaurantName;
    private String contactPerson;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String cityName;
    private Double latitude;
    private Double longitude;
    private String status; // PENDING, APPROVED, REJECTED
    private Instant createdAt;
    private String requestSource; // CUSTOMER, DELIVERY_PARTNER, RESTAURANT
    private String requesterName;
    private String requestedArea;
    private String zoneId;
    private String zoneName;
    private String restaurantId;
    private Double distanceFromZoneKm;
    private List<String> submittedDocuments;

    public UnserviceableRequestDto() {}

    public UnserviceableRequestDto(
            String id,
            String restaurantName,
            String contactPerson,
            String contactEmail,
            String contactPhone,
            String address,
            String cityName,
            Double latitude,
            Double longitude,
            String status,
            Instant createdAt) {
        this.id = id;
        this.restaurantName = restaurantName;
        this.contactPerson = contactPerson;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.cityName = cityName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UnserviceableRequestDto(
            String id,
            String restaurantName,
            String contactPerson,
            String contactEmail,
            String contactPhone,
            String address,
            String cityName,
            Double latitude,
            Double longitude,
            String status,
            Instant createdAt,
            String requestSource,
            String requesterName,
            String requestedArea,
            String zoneId,
            String zoneName) {
        this(id, restaurantName, contactPerson, contactEmail, contactPhone, address, cityName, latitude, longitude, status, createdAt);
        this.requestSource = requestSource;
        this.requesterName = requesterName;
        this.requestedArea = requestedArea;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeliveryPartnerId() {
        return deliveryPartnerId;
    }

    public void setDeliveryPartnerId(String deliveryPartnerId) {
        this.deliveryPartnerId = deliveryPartnerId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getRequestSource() {
        return requestSource;
    }

    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getRequestedArea() {
        return requestedArea;
    }

    public void setRequestedArea(String requestedArea) {
        this.requestedArea = requestedArea;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public List<String> getSubmittedDocuments() {
        return submittedDocuments;
    }

    public void setSubmittedDocuments(List<String> submittedDocuments) {
        this.submittedDocuments = submittedDocuments;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(String restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Double getDistanceFromZoneKm() {
        return distanceFromZoneKm;
    }

    public void setDistanceFromZoneKm(Double distanceFromZoneKm) {
        this.distanceFromZoneKm = distanceFromZoneKm;
    }
}
