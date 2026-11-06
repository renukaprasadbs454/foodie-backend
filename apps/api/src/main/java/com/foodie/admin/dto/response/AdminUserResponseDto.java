package com.foodie.admin.dto.response;

import com.foodie.admin.entity.AdminRoleName;
import java.util.UUID;

public record AdminUserResponseDto(
        UUID adminUserId,
        UUID userCredentialId,
        String fullName,
        AdminRoleName role,
        String profileImageKey,
        String assignedZoneId,
        String email,
        String phoneNumber,
        Integer loginDevicesCount,
        Integer activeDevicesCount
) {
    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey,
            String assignedZoneId,
            String email,
            String phoneNumber
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, assignedZoneId, email, phoneNumber, 0, 0);
    }

    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey,
            String assignedZoneId
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, assignedZoneId, null, null, 0, 0);
    }

    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, null, null, null, 0, 0);
    }
}
