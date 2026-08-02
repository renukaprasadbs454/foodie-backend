package com.foodie.shared.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow cross-module read of customer display fields (Phase3 §2.2).
 * Order / Review / Coupon must use this — never User repositories.
 */
public interface CustomerSummaryProvider {

    Optional<CustomerSummary> findByCustomerId(UUID customerId);

    record CustomerSummary(UUID customerId, String fullName, String profileImageKey) {
    }
}
