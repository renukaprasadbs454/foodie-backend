package com.foodie.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record ZoneSelectionResponseDto(
        UUID sessionRecordId,
        String zoneId,
        String zoneName,
        String cityName,
        String adminName,
        String adminEmail,
        String role,
        Instant loginTime,
        String sessionStatus
) {}
