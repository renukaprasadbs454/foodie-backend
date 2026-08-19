package com.foodie.tax.repository;

import com.foodie.tax.entity.TaxRule;
import com.foodie.tax.enums.PricingComponentType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {

    @Query("""
        SELECT r FROM TaxRule r
        WHERE r.componentType = :componentType
          AND r.active = true
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
        ORDER BY r.priority DESC, r.version DESC
    """)
    List<TaxRule> findActiveRulesForComponent(
            @Param("componentType") PricingComponentType componentType,
            @Param("date") LocalDate date
    );
}
