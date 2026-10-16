package com.foodie.admin.dto.response;

import com.foodie.admin.entity.AdminRoleName;
import java.util.UUID;

public record AdminUserResponseDto(
        UUID adminUserId,
        UUID userCredentialId,
        String fullName,
        AdminRoleName role,
        String profileImageKey,
        String assignedZoneId
) {
    public AdminUserResponseDto(
            UUID adminUserId,
            UUID userCredentialId,
            String fullName,
            AdminRoleName role,
            String profileImageKey
    ) {
        this(adminUserId, userCredentialId, fullName, role, profileImageKey, null);
    }
}
