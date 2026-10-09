package com.foodie.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationBroadcastResponseDto(
        UUID id,
        String title,
        String body,
        String targetAudience,
        String actionUrl,
        Instant scheduledAt,
        Instant sentAt,
        int recipientsCount,
        String deliveryStatus
) {
}
