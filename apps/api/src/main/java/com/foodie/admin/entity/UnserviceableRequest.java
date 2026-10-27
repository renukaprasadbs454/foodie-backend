package com.foodie.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unserviceable_request")
public class UnserviceableRequest {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "delivery_partner_id")
    private UUID deliveryPartnerId;

    @Column(name = "restaurant_name", length = 255)
    private String restaurantName;

    @Column(name = "contact_person", length = 255)
    private String contactPerson;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "city_name", length = 100)
    private String cityName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "status", length = 50, nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "request_source", length = 50, nullable = false)
    private String requestSource = "CUSTOMER";

    @Column(name = "requester_name", length = 255)
    private String requesterName;

    @Column(name = "requested_area", columnDefinition = "TEXT")
    private String requestedArea;

    @Column(name = "zone_id", length = 100)
    private String zoneId;

    @Column(name = "zone_name", length = 200)
    private String zoneName;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "distance_from_zone_km")
    private Double distanceFromZoneKm;

    @Column(name = "submitted_documents", columnDefinition = "TEXT")
    private String submittedDocuments;

    public UnserviceableRequest() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getDeliveryPartnerId() {
        return deliveryPartnerId;
    }

    public void setDeliveryPartnerId(UUID deliveryPartnerId) {
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
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
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

    public String getSubmittedDocuments() {
        return submittedDocuments;
    }

    public void setSubmittedDocuments(String submittedDocuments) {
        this.submittedDocuments = submittedDocuments;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(UUID restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Double getDistanceFromZoneKm() {
        return distanceFromZoneKm;
    }

    public void setDistanceFromZoneKm(Double distanceFromZoneKm) {
        this.distanceFromZoneKm = distanceFromZoneKm;
    }
}
