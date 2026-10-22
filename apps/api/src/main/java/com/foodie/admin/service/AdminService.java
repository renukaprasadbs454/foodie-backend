package com.foodie.admin.service;

import com.foodie.admin.dto.response.AdminUserResponseDto;
import com.foodie.admin.entity.AdminRoleName;
import java.util.UUID;

/**
 * Admin public interface (Phase3 §2.13).
 */
public interface AdminService {

    record CreateAdminUserCommand(
            String fullName,
            String phoneNumber,
            String email,
            AdminRoleName role,
            String password,
            String assignedZoneId
    ) {
        public CreateAdminUserCommand(
                String fullName,
                String phoneNumber,
                String email,
                AdminRoleName role
        ) {
            this(fullName, phoneNumber, email, role, null, null);
        }
    }

    record ActorContext(UUID actorUserCredentialId) {
    }

    record AdminUserView(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey,
            String assignedZoneId,
            String email,
            String phoneNumber
    ) {
        public AdminUserView(
                UUID adminUserId,
                UUID userCredentialId,
                String fullName,
                AdminRoleName role,
                String profileImageKey,
                String assignedZoneId
        ) {
            this(adminUserId, userCredentialId, fullName, role, profileImageKey, assignedZoneId, null, null);
        }

        public AdminUserView(
                UUID adminUserId,
                UUID userCredentialId,
                String fullName,
                AdminRoleName role,
                String profileImageKey
        ) {
            this(adminUserId, userCredentialId, fullName, role, profileImageKey, null, null, null);
        }
    }

    AdminUserView createAdminUser(CreateAdminUserCommand cmd, ActorContext creator);

    java.util.List<AdminUserView> listAllAdminUsers();

    void deleteAdminUser(UUID adminUserId);

    void deleteAdminUsersByZoneId(String zoneId);

    boolean hasPermission(UUID adminUserId, String resource, String action);

    void recordAudit(
            UUID adminUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Object before,
            Object after
    );

    AdminUserView requireAdminProfile(UUID userCredentialId);

    AdminUserResponseDto toResponse(AdminUserView view);
}
