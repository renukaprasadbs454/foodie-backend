package com.foodie.shared.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow cross-module read of restaurant display fields (Phase3 §2.3).
 */
public interface RestaurantSummaryProvider {

    Optional<RestaurantSummary> findByRestaurantId(UUID restaurantId);

    record RestaurantSummary(
            UUID restaurantId,
            String name,
            String status,
            String logoImageKey
    ) {
    }
}
