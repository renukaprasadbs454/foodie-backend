package com.foodie.shared.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow cross-module read of restaurant display fields (Phase3 §2.3).
 */
public interface RestaurantSummaryProvider {

    Optional<RestaurantSummary> findByRestaurantId(UUID restaurantId);

    /** Used by Menu (and similar) to resolve the caller's owned restaurant without reading Restaurant tables. */
    Optional<RestaurantSummary> findByOwnerUserCredentialId(UUID ownerUserCredentialId);

    record RestaurantSummary(
            UUID restaurantId,
            String name,
            String status,
            String logoImageKey
    ) {
    }
}
