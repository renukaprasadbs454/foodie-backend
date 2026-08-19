package com.foodie.tax.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tax_snapshots")
public class TaxSnapshot extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "seller_entity_id")
    private UUID sellerEntityId;

    @Column(name = "customer_tax_profile_id")
    private UUID customerTaxProfileId;

    @Column(name = "supply_context", columnDefinition = "TEXT")
    private String supplyContext;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "total_taxable_paise", nullable = false)
    private long totalTaxablePaise;

    @Column(name = "total_cgst_paise", nullable = false)
    private long totalCgstPaise;

    @Column(name = "total_sgst_paise", nullable = false)
    private long totalSgstPaise;

    @Column(name = "total_igst_paise", nullable = false)
    private long totalIgstPaise;

    @Column(name = "total_cess_paise", nullable = false)
    private long totalCessPaise;

    @Column(name = "rounding_adjustment_paise", nullable = false)
    private long roundingAdjustmentPaise;

    @Column(name = "tax_rule_set_version", nullable = false)
    private int taxRuleSetVersion = 1;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;

    @OneToMany(mappedBy = "taxSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaxSnapshotItem> items = new ArrayList<>();

    protected TaxSnapshot() {
    }

    public static TaxSnapshot create(
            UUID orderId,
            UUID sellerEntityId,
            UUID customerTaxProfileId,
            String supplyContext,
            String currency,
            long totalTaxablePaise,
            long totalCgstPaise,
            long totalSgstPaise,
            long totalIgstPaise,
            long totalCessPaise,
            long roundingAdjustmentPaise,
            int taxRuleSetVersion
    ) {
        TaxSnapshot snapshot = new TaxSnapshot();
        snapshot.orderId = orderId;
        snapshot.sellerEntityId = sellerEntityId;
        snapshot.customerTaxProfileId = customerTaxProfileId;
        snapshot.supplyContext = supplyContext;
        snapshot.currency = currency != null ? currency : "INR";
        snapshot.totalTaxablePaise = totalTaxablePaise;
        snapshot.totalCgstPaise = totalCgstPaise;
        snapshot.totalSgstPaise = totalSgstPaise;
        snapshot.totalIgstPaise = totalIgstPaise;
        snapshot.totalCessPaise = totalCessPaise;
        snapshot.roundingAdjustmentPaise = roundingAdjustmentPaise;
        snapshot.taxRuleSetVersion = taxRuleSetVersion;
        snapshot.calculatedAt = Instant.now();
        return snapshot;
    }

    public void addItem(TaxSnapshotItem item) {
        items.add(item);
        item.setTaxSnapshot(this);
    }

    public void attachInvoice(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getSellerEntityId() {
        return sellerEntityId;
    }

    public UUID getCustomerTaxProfileId() {
        return customerTaxProfileId;
    }

    public String getSupplyContext() {
        return supplyContext;
    }

    public String getCurrency() {
        return currency;
    }

    public long getTotalTaxablePaise() {
        return totalTaxablePaise;
    }

    public long getTotalCgstPaise() {
        return totalCgstPaise;
    }

    public long getTotalSgstPaise() {
        return totalSgstPaise;
    }

    public long getTotalIgstPaise() {
        return totalIgstPaise;
    }

    public long getTotalCessPaise() {
        return totalCessPaise;
    }

    public long getTotalTaxPaise() {
        return totalCgstPaise + totalSgstPaise + totalIgstPaise + totalCessPaise;
    }

    public long getRoundingAdjustmentPaise() {
        return roundingAdjustmentPaise;
    }

    public int getTaxRuleSetVersion() {
        return taxRuleSetVersion;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public List<TaxSnapshotItem> getItems() {
        return items;
    }
}
