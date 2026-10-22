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
        String phoneNumber
) {
    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey,
            String assignedZoneId
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, assignedZoneId, null, null);
    }

    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, null, null, null);
    }
}
