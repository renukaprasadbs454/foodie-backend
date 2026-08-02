package com.foodie.shared.contract;

import java.util.Optional;
import java.util.UUID;

/** Narrow lookup for Order visibility (delivery partner owns assignment). */
public interface DeliveryPartnerLookup {

    Optional<UUID> findPartnerIdByUserCredentialId(UUID userCredentialId);
}
