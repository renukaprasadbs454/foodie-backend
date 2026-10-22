package com.foodie.delivery.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeliveryShiftResponseDto(
        UUID shiftId,
        String shiftName,
        String startTime,
        String endTime,
        String durationHours,
        String status,
        String zoneId,
        String zoneName,
        Instant createdAt,
        Instant updatedAt
) {
    public DeliveryShiftResponseDto(
            UUID shiftId,
            String shiftName,
            String startTime,
            String endTime,
            String durationHours,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(shiftId, shiftName, startTime, endTime, durationHours, status, null, null, createdAt, updatedAt);
    }
}
