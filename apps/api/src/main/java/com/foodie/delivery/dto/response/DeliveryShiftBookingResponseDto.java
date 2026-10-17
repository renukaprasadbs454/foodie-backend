package com.foodie.delivery.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeliveryShiftBookingResponseDto(
        UUID bookingId,
        UUID deliveryPartnerId,
        UUID shiftId,
        String shiftName,
        String startTime,
        String endTime,
        String durationHours,
        String status,
        Instant bookedAt,
        Instant createdAt,
        Instant updatedAt
) {}
