package com.foodie.notification.service;

import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.NotificationEventType;
import com.foodie.notification.dto.request.SendBroadcastNotificationRequestDto;
import com.foodie.notification.dto.request.UpdateNotificationPreferenceRequestDto;
import com.foodie.notification.dto.response.NotificationBroadcastResponseDto;
import com.foodie.notification.dto.response.NotificationPreferenceResponseDto;
import com.foodie.notification.dto.response.NotificationReadResponseDto;
import com.foodie.notification.dto.response.NotificationResponseDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    void send(UUID userCredentialId, NotificationEventType eventType, Map<String, String> params);

    NotificationBroadcastResponseDto sendBroadcast(SendBroadcastNotificationRequestDto request);

    PageResult<NotificationBroadcastResponseDto> getBroadcastHistory(String audience, int page, int size);

    PageResult<NotificationResponseDto> list(UUID userCredentialId, boolean unreadOnly, int page, int size);

    long countUnread(UUID userCredentialId);

    NotificationReadResponseDto markRead(UUID userCredentialId, UUID notificationLogId);

    int markAllRead(UUID userCredentialId);

    NotificationPreferenceResponseDto getPreferences(UUID userCredentialId);

    NotificationPreferenceResponseDto updatePreferences(
            UUID userCredentialId, UpdateNotificationPreferenceRequestDto request);

    void registerDeviceToken(UUID userCredentialId, String deviceToken);

    record PageResult<T>(List<T> items, PaginationMeta pagination) {
    }
}
