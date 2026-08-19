package com.foodie.tax.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_tax_profiles")
public class CustomerTaxProfile extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_type", nullable = false, length = 20)
    private String customerType = "INDIVIDUAL";

    @Column(name = "legal_name", length = 150)
    private String legalName;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "state_code", length = 50)
    private String stateCode;

    @Column(name = "country_code", length = 10)
    private String countryCode = "IN";

    @Column(name = "gstin_verified_at")
    private Instant gstinVerifiedAt;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = true;

    protected CustomerTaxProfile() {
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerType() {
        return customerType;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getGstin() {
        return gstin;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public Instant getGstinVerifiedAt() {
        return gstinVerifiedAt;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
