package com.foodie.tax.model;

import com.foodie.tax.enums.DiscountFundingSource;

public record DiscountInput(
        long discountAmountPaise,
        DiscountFundingSource fundingSource,
        String reason
) {
}
