package com.foodie.auth.dto.response;

public record AdminRoleOptionDto(
        String key,
        String role,
        String label,
        String zoneId,
        String zoneName,
        String defaultEmail
) {
}
