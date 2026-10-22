package com.foodie.shared.contract;

import java.util.UUID;

/**
 * Auth-owned provisioning for Admin users (Phase3 §2.13 / §5.1).
 * ADMIN credentials are never self-registered via OTP signup.
 */
public interface AdminCredentialPort {

    /**
     * Creates an active {@code user_credential} with {@code userType=ADMIN} for phone OTP login,
     * or returns the existing ADMIN credential id for the phone.
     */
    UUID ensureAdminCredential(String phoneNumber, String email);

    /**
     * Creates or updates an active {@code user_credential} with {@code userType=ADMIN}
     * and a securely BCrypt-hashed password for administrative login.
     */
    UUID ensureAdminCredentialWithPassword(String phoneNumber, String email, String password);

    /**
     * Permanently deletes a user_credential record belonging to an administrator.
     */
    void deleteAdminCredential(UUID userCredentialId);

    /**
     * Retrieves email associated with an admin credential.
     */
    java.util.Optional<String> findEmailByUserCredentialId(UUID userCredentialId);

    /**
     * Retrieves phone number associated with an admin credential.
     */
    java.util.Optional<String> findPhoneNumberByUserCredentialId(UUID userCredentialId);
}

