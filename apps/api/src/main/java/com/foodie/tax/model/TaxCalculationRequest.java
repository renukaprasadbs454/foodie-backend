package com.foodie.tax.model;

import java.util.List;
import java.util.UUID;

public record TaxCalculationRequest(
        UUID orderId,
        UUID sellerEntityId,
        UUID customerTaxProfileId,
        TaxContext taxContext,
        List<TaxComponentInput> components,
        List<DiscountInput> discounts
) {
}
