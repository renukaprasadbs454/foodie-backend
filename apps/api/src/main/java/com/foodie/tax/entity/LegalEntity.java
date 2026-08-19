package com.foodie.tax.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "legal_entities")
public class LegalEntity extends BaseEntity {

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "trade_name", length = 150)
    private String tradeName;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "state_code", nullable = false, length = 50)
    private String stateCode;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected LegalEntity() {
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getPan() {
        return pan;
    }

    public String getGstin() {
        return gstin;
    }

    public String getRegisteredAddress() {
        return registeredAddress;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getEntityType() {
        return entityType;
    }

    public boolean isActive() {
        return active;
    }
}
