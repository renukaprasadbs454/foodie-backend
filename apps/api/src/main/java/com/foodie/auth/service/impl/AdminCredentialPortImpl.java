package com.foodie.auth.service.impl;

import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.enums.UserType;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.shared.contract.AdminCredentialPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCredentialPortImpl implements AdminCredentialPort {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminCredentialPortImpl(
            UserCredentialRepository userCredentialRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userCredentialRepository = userCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UUID ensureAdminCredential(String phoneNumber, String email) {
        return ensureAdminCredentialWithPassword(phoneNumber, email, null);
    }

    @Override
    @Transactional
    public UUID ensureAdminCredentialWithPassword(String phoneNumber, String email, String password) {
        var existingForPhone = (phoneNumber != null && !phoneNumber.isBlank())
                ? userCredentialRepository.findAllByPhoneNumber(phoneNumber)
                : java.util.List.<UserCredential>of();
        Optional<UserCredential> existingAdmin = existingForPhone.stream()
                .filter(c -> c.getUserType() == UserType.ADMIN)
                .findFirst();
        if (existingAdmin.isEmpty() && email != null && !email.isBlank()) {
            existingAdmin = userCredentialRepository.findByEmailIgnoreCaseAndUserType(email.trim(), UserType.ADMIN);
        }
        if (existingAdmin.isPresent()) {
            UserCredential cred = existingAdmin.get();
            if (password != null && !password.isBlank()) {
                cred.updatePasswordHash(passwordEncoder.encode(password));
            }
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                cred.updatePhoneNumber(phoneNumber);
            }
            userCredentialRepository.save(cred);
            return cred.getId();
        }
        boolean usedByNonAdmin = existingForPhone.stream()
                .anyMatch(c -> c.getUserType() != UserType.ADMIN);
        if (usedByNonAdmin) {
            throw new ConflictException(
                    ErrorCode.CONFLICT,
                    "Phone number is already registered to a non-admin account."
            );
        }

        UserCredential newCred;
        if (password != null && !password.isBlank()) {
            newCred = UserCredential.adminProvisionWithPassword(phoneNumber, email, passwordEncoder.encode(password));
        } else {
            newCred = UserCredential.adminProvision(phoneNumber, email);
        }
        return userCredentialRepository.save(newCred).getId();
    }

    @Override
    @Transactional
    public void deleteAdminCredential(UUID userCredentialId) {
        if (userCredentialId != null && userCredentialRepository.existsById(userCredentialId)) {
            userCredentialRepository.deleteById(userCredentialId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findEmailByUserCredentialId(UUID userCredentialId) {
        if (userCredentialId == null) return Optional.empty();
        return userCredentialRepository.findById(userCredentialId).map(UserCredential::getEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findPhoneNumberByUserCredentialId(UUID userCredentialId) {
        if (userCredentialId == null) return Optional.empty();
        return userCredentialRepository.findById(userCredentialId).map(UserCredential::getPhoneNumber);
    }
}

