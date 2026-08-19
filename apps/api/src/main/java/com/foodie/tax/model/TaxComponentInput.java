package com.foodie.tax.model;

import com.foodie.tax.enums.PricingComponentType;
import java.util.UUID;

public record TaxComponentInput(
        PricingComponentType componentType,
        String description,
        long grossPaise,
        long discountPaise,
        UUID orderItemId
) {
    public TaxComponentInput(PricingComponentType componentType, String description, long grossPaise, long discountPaise) {
        this(componentType, description, grossPaise, discountPaise, null);
    }
}
