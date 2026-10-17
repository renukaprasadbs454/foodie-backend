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
        Instant createdAt,
        Instant updatedAt
) {}
