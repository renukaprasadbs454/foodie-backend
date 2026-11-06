package com.foodie.admin.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserLoginDeviceResponseDto(
        UUID id,
        String userId,
        String userRole,
        String deviceName,
        String browser,
        String os,
        String ipAddress,
        String location,
        Instant loginAt,
        Instant lastActiveAt,
        boolean isCurrentActive,
        Instant logoutAt
) {
}

