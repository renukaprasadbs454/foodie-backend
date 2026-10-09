package com.foodie.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record SendBroadcastNotificationRequestDto(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Body is required") String body,
        @NotBlank(message = "Target audience is required") String targetAudience,
        String actionUrl,
        Instant scheduledAt
) {
}
