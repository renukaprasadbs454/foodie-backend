package com.foodie.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponseDto(
        UUID notificationLogId,
        String title,
        String body,
        String actionUrl,
        String targetAudience,
        Instant sentAt,
        Instant readAt
) {
    public NotificationResponseDto(UUID notificationLogId, String title, String body, Instant sentAt, Instant readAt) {
        this(notificationLogId, title, body, null, null, sentAt, readAt);
    }
}
