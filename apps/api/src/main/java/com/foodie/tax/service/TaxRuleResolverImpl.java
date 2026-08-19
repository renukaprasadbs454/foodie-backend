package com.foodie.tax.service;

import com.foodie.tax.entity.TaxRule;
import com.foodie.tax.enums.PricingComponentType;
import com.foodie.tax.enums.TaxType;
import com.foodie.tax.model.TaxContext;
import com.foodie.tax.repository.TaxRuleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TaxRuleResolverImpl implements TaxRuleResolver {

    private final TaxRuleRepository taxRuleRepository;

    public TaxRuleResolverImpl(TaxRuleRepository taxRuleRepository) {
        this.taxRuleRepository = taxRuleRepository;
    }

    @Override
    public Optional<TaxRule> resolveRule(PricingComponentType componentType, TaxContext context, LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        List<TaxRule> activeRules = taxRuleRepository.findActiveRulesForComponent(componentType, effectiveDate);

        if (activeRules.isEmpty()) {
            return Optional.empty();
        }

        // Filter based on intra-state (CGST_SGST) vs inter-state (IGST) supply context if available
        if (context != null) {
            TaxType requiredType = context.intraState() ? TaxType.CGST_SGST : TaxType.IGST;
            Optional<TaxRule> matched = activeRules.stream()
                    .filter(r -> r.getTaxType() == requiredType)
                    .findFirst();
            if (matched.isPresent()) {
                return matched;
            }
        }

        return Optional.of(activeRules.getFirst());
    }
}
