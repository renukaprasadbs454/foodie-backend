package com.foodie.tax.model;

import com.foodie.tax.entity.TaxSnapshot;
import com.foodie.tax.entity.TaxSnapshotItem;
import java.util.List;

public record TaxCalculationResult(
        long totalTaxablePaise,
        long totalCgstPaise,
        long totalSgstPaise,
        long totalIgstPaise,
        long totalCessPaise,
        long totalTaxPaise,
        long roundingAdjustmentPaise,
        long grandTotalPaise,
        TaxSnapshot snapshot,
        List<TaxSnapshotItem> items
) {
}
