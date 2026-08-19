package com.foodie.tax.service;

import com.foodie.tax.entity.TaxRule;
import com.foodie.tax.enums.PricingComponentType;
import com.foodie.tax.model.TaxContext;
import java.time.LocalDate;
import java.util.Optional;

public interface TaxRuleResolver {
    Optional<TaxRule> resolveRule(PricingComponentType componentType, TaxContext context, LocalDate date);
}
