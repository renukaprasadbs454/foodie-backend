package com.foodie.auth.repository;

import com.foodie.auth.entity.UserCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByPhoneNumber(String phoneNumber);
    Optional<UserCredential> findByGoogleId(String googleId);
}
