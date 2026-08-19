package com.foodie.tax.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.tax.enums.PricingComponentType;
import com.foodie.tax.enums.TaxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_rules")
public class TaxRule extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 50)
    private PricingComponentType componentType;

    @Column(name = "tax_category", nullable = false, length = 50)
    private String taxCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 20)
    private TaxType taxType;

    @Column(name = "cgst_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(name = "sgst_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(name = "igst_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cessRate = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "seller_scope", length = 100)
    private String sellerScope;

    @Column(name = "location_scope", length = 100)
    private String locationScope;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected TaxRule() {
    }

    public static TaxRule create(
            String name,
            PricingComponentType componentType,
            String taxCategory,
            TaxType taxType,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            BigDecimal igstRate,
            BigDecimal cessRate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int priority,
            int version
    ) {
        TaxRule rule = new TaxRule();
        rule.name = name;
        rule.componentType = componentType;
        rule.taxCategory = taxCategory;
        rule.taxType = taxType;
        rule.cgstRate = cgstRate != null ? cgstRate : BigDecimal.ZERO;
        rule.sgstRate = sgstRate != null ? sgstRate : BigDecimal.ZERO;
        rule.igstRate = igstRate != null ? igstRate : BigDecimal.ZERO;
        rule.cessRate = cessRate != null ? cessRate : BigDecimal.ZERO;
        rule.effectiveFrom = effectiveFrom;
        rule.effectiveTo = effectiveTo;
        rule.priority = priority;
        rule.version = version;
        rule.active = true;
        return rule;
    }

    public String getName() {
        return name;
    }

    public PricingComponentType getComponentType() {
        return componentType;
    }

    public String getTaxCategory() {
        return taxCategory;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    public BigDecimal getCgstRate() {
        return cgstRate;
    }

    public BigDecimal getSgstRate() {
        return sgstRate;
    }

    public BigDecimal getIgstRate() {
        return igstRate;
    }

    public BigDecimal getCessRate() {
        return cessRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getSellerScope() {
        return sellerScope;
    }

    public String getLocationScope() {
        return locationScope;
    }

    public String getConditions() {
        return conditions;
    }

    public int getPriority() {
        return priority;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }
}
