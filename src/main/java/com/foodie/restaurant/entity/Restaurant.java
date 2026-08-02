package com.foodie.restaurant.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.RestaurantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "restaurant")
public class Restaurant extends BaseEntity {

    @Column(name = "owner_user_credential_id", nullable = false, unique = true, updatable = false)
    private UUID ownerUserCredentialId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "cuisine_types", columnDefinition = "text[]", nullable = false)
    private String[] cuisineTypes;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    private RestaurantAddress address;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "logo_image_key", length = 500)
    private String logoImageKey;

    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "avg_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RestaurantStatus status;

    @Column(name = "commission_pct", nullable = false, precision = 4, scale = 2)
    private BigDecimal commissionPct;

    protected Restaurant() {
    }

    public static Restaurant createPending(
            UUID ownerUserCredentialId,
            String name,
            String description,
            String[] cuisineTypes,
            RestaurantAddress address,
            BigDecimal commissionPct
    ) {
        Restaurant restaurant = new Restaurant();
        restaurant.ownerUserCredentialId = ownerUserCredentialId;
        restaurant.name = name;
        restaurant.description = description;
        restaurant.cuisineTypes = cuisineTypes;
        restaurant.address = address;
        restaurant.latitude = address.getLatitude();
        restaurant.longitude = address.getLongitude();
        restaurant.avgRating = BigDecimal.ZERO.setScale(1);
        restaurant.status = RestaurantStatus.PENDING;
        restaurant.commissionPct = commissionPct;
        return restaurant;
    }

    public void updateProfile(String name, String description, String[] cuisineTypes) {
        this.name = name;
        this.description = description;
        this.cuisineTypes = cuisineTypes;
    }

    public void syncGeoFromAddress() {
        this.latitude = address.getLatitude();
        this.longitude = address.getLongitude();
    }

    public void approve() {
        this.status = RestaurantStatus.APPROVED;
    }

    public void suspend() {
        this.status = RestaurantStatus.SUSPENDED;
    }

    public void setLogoImageKey(String logoImageKey) {
        this.logoImageKey = logoImageKey;
    }

    public void setCoverImageKey(String coverImageKey) {
        this.coverImageKey = coverImageKey;
    }

    public UUID getOwnerUserCredentialId() {
        return ownerUserCredentialId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getCuisineTypes() {
        return cuisineTypes;
    }

    public RestaurantAddress getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getLogoImageKey() {
        return logoImageKey;
    }

    public String getCoverImageKey() {
        return coverImageKey;
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public RestaurantStatus getStatus() {
        return status;
    }

    public BigDecimal getCommissionPct() {
        return commissionPct;
    }
}
