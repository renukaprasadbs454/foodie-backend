package com.foodie.restaurant.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "restaurant_address")
public class RestaurantAddress extends BaseEntity {

    @Column(name = "line1", nullable = false)
    private String line1;

    @Column(name = "line2")
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    protected RestaurantAddress() {
    }

    public static RestaurantAddress create(
            String line1,
            String line2,
            String city,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        RestaurantAddress address = new RestaurantAddress();
        address.line1 = line1;
        address.line2 = line2;
        address.city = city;
        address.pincode = pincode;
        address.latitude = latitude;
        address.longitude = longitude;
        return address;
    }

    public void replace(
            String line1,
            String line2,
            String city,
            String pincode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.pincode = pincode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getPincode() {
        return pincode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
