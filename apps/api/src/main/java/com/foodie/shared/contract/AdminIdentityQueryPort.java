package com.foodie.shared.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Auth / Delivery → Admin identity lookup without creating an auth→admin package cycle.
 * Used by Admin email/password login, audit logs, and delivery pricing config tracking.
 */
public interface AdminIdentityQueryPort {

    /** Binding Admin role name (e.g. SUPER_ADMIN), if an admin_user row exists. */
    Optional<String> findRoleNameByUserCredentialId(UUID userCredentialId);

    /** Resolves admin_user.id from user_credential.id */
    Optional<UUID> findAdminUserIdByUserCredentialId(UUID userCredentialId);

    /** Resolves assigned_zone_id if configured on admin_user. */
    default Optional<String> findAssignedZoneIdByUserCredentialId(UUID userCredentialId) {
        return Optional.empty();
    }
}
