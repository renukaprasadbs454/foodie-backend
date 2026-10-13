package com.foodie.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminZoneLoginHistoryDto(
        UUID id,
        UUID adminUserId,
        String adminName,
        String adminEmail,
        String role,
        String zoneId,
        String zoneName,
        Instant loginTime,
        String sessionStatus,
        String ipAddress,
        String userAgent
) {}
