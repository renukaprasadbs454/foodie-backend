package com.foodie.tax.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.tax.enums.PricingComponentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tax_snapshot_items")
public class TaxSnapshotItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_snapshot_id", nullable = false)
    private TaxSnapshot taxSnapshot;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, length = 50)
    private PricingComponentType componentType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "gross_paise", nullable = false)
    private long grossPaise;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "taxable_paise", nullable = false)
    private long taxablePaise;

    @Column(name = "tax_rule_id")
    private UUID taxRuleId;

    @Column(name = "tax_rule_version", nullable = false)
    private int taxRuleVersion;

    @Column(name = "cgst_paise", nullable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false)
    private long igstPaise;

    @Column(name = "cess_paise", nullable = false)
    private long cessPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    protected TaxSnapshotItem() {
    }

    public static TaxSnapshotItem create(
            UUID orderItemId,
            PricingComponentType componentType,
            String description,
            long grossPaise,
            long discountPaise,
            long taxablePaise,
            UUID taxRuleId,
            int taxRuleVersion,
            long cgstPaise,
            long sgstPaise,
            long igstPaise,
            long cessPaise,
            long totalPaise
    ) {
        TaxSnapshotItem item = new TaxSnapshotItem();
        item.orderItemId = orderItemId;
        item.componentType = componentType;
        item.description = description;
        item.grossPaise = grossPaise;
        item.discountPaise = discountPaise;
        item.taxablePaise = taxablePaise;
        item.taxRuleId = taxRuleId;
        item.taxRuleVersion = taxRuleVersion;
        item.cgstPaise = cgstPaise;
        item.sgstPaise = sgstPaise;
        item.igstPaise = igstPaise;
        item.cessPaise = cessPaise;
        item.totalPaise = totalPaise;
        return item;
    }

    public void setTaxSnapshot(TaxSnapshot taxSnapshot) {
        this.taxSnapshot = taxSnapshot;
    }

    public TaxSnapshot getTaxSnapshot() {
        return taxSnapshot;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public PricingComponentType getComponentType() {
        return componentType;
    }

    public String getDescription() {
        return description;
    }

    public long getGrossPaise() {
        return grossPaise;
    }

    public long getDiscountPaise() {
        return discountPaise;
    }

    public long getTaxablePaise() {
        return taxablePaise;
    }

    public UUID getTaxRuleId() {
        return taxRuleId;
    }

    public int getTaxRuleVersion() {
        return taxRuleVersion;
    }

    public long getCgstPaise() {
        return cgstPaise;
    }

    public long getSgstPaise() {
        return sgstPaise;
    }

    public long getIgstPaise() {
        return igstPaise;
    }

    public long getCessPaise() {
        return cessPaise;
    }

    public long getTotalPaise() {
        return totalPaise;
    }
}
