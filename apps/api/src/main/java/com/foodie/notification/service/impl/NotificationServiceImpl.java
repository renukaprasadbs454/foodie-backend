package com.foodie.notification.service.impl;

import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.dto.PaginationMeta;
import com.foodie.common.enums.NotificationChannel;
import com.foodie.common.enums.NotificationDeliveryStatus;
import com.foodie.common.enums.NotificationEventType;
import com.foodie.common.enums.UserType;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.infrastructure.fcm.FcmClient;
import com.foodie.notification.dto.request.SendBroadcastNotificationRequestDto;
import com.foodie.notification.dto.request.UpdateNotificationPreferenceRequestDto;
import com.foodie.notification.dto.response.NotificationBroadcastResponseDto;
import com.foodie.notification.dto.response.NotificationPreferenceResponseDto;
import com.foodie.notification.dto.response.NotificationReadResponseDto;
import com.foodie.notification.dto.response.NotificationResponseDto;
import com.foodie.notification.entity.NotificationBroadcast;
import com.foodie.notification.entity.NotificationLog;
import com.foodie.notification.entity.NotificationTemplate;
import com.foodie.notification.mapper.NotificationMapper;
import com.foodie.notification.repository.NotificationBroadcastRepository;
import com.foodie.notification.repository.NotificationLogRepository;
import com.foodie.notification.repository.NotificationTemplateRepository;
import com.foodie.notification.service.DeviceTokenStore;
import com.foodie.notification.service.NotificationPreferenceStore;
import com.foodie.notification.service.NotificationService;
import com.foodie.notification.service.TemplateRenderer;
import com.foodie.shared.event.NotificationDispatchedEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final NotificationBroadcastRepository broadcastRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final TemplateRenderer templateRenderer;
    private final NotificationPreferenceStore preferenceStore;
    private final DeviceTokenStore deviceTokenStore;
    private final FcmClient fcmClient;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public NotificationServiceImpl(
            NotificationTemplateRepository templateRepository,
            NotificationLogRepository logRepository,
            NotificationBroadcastRepository broadcastRepository,
            UserCredentialRepository userCredentialRepository,
            TemplateRenderer templateRenderer,
            NotificationPreferenceStore preferenceStore,
            DeviceTokenStore deviceTokenStore,
            FcmClient fcmClient,
            ApplicationEventPublisher eventPublisher) {
        this.templateRepository = templateRepository;
        this.logRepository = logRepository;
        this.broadcastRepository = broadcastRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.templateRenderer = templateRenderer;
        this.preferenceStore = preferenceStore;
        this.deviceTokenStore = deviceTokenStore;
        this.fcmClient = fcmClient;
        this.eventPublisher = eventPublisher;
    }

    public NotificationServiceImpl(
            NotificationTemplateRepository templateRepository,
            NotificationLogRepository logRepository,
            TemplateRenderer templateRenderer,
            NotificationPreferenceStore preferenceStore,
            DeviceTokenStore deviceTokenStore,
            FcmClient fcmClient,
            ApplicationEventPublisher eventPublisher) {
        this(templateRepository, logRepository, null, null, templateRenderer, preferenceStore, deviceTokenStore, fcmClient, eventPublisher);
    }

    @Override
    @Transactional
    public void send(UUID userCredentialId, NotificationEventType eventType, Map<String, String> params) {
        if (userCredentialId == null || eventType == null) {
            return;
        }

        NotificationTemplate template = templateRepository
                .findByEventTypeAndChannel(eventType, NotificationChannel.PUSH)
                .orElse(null);
        if (template == null) {
            log.warn("No PUSH template for eventType={} — skipping", eventType);
            return;
        }

        String title = templateRenderer.render(template.getTitleTemplate(), params);
        String body = templateRenderer.render(template.getBodyTemplate(), params);

        if (!preferenceStore.isEnabled(userCredentialId, NotificationChannel.PUSH)) {
            NotificationLog skipped = logRepository.save(NotificationLog.create(
                    userCredentialId,
                    template.getId(),
                    title,
                    body,
                    NotificationDeliveryStatus.SKIPPED));
            publishDispatched(skipped);
            return;
        }

        NotificationLog entry = logRepository.save(NotificationLog.create(
                userCredentialId,
                template.getId(),
                title,
                body,
                NotificationDeliveryStatus.PENDING));

        try {
            String token = deviceTokenStore.find(userCredentialId).orElse(null);
            fcmClient.sendPush(userCredentialId, token, title, body);
            entry.markDeliveryStatus(NotificationDeliveryStatus.SENT);
        } catch (RuntimeException ex) {
            log.error("FCM delivery failed for user={} eventType={}: {}",
                    userCredentialId, eventType, ex.getMessage());
            entry.markDeliveryStatus(NotificationDeliveryStatus.FAILED);
        }
        logRepository.save(entry);
        publishDispatched(entry);
    }

    @Override
    @Transactional
    public NotificationBroadcastResponseDto sendBroadcast(SendBroadcastNotificationRequestDto request) {
        String canonicalAudience = normalizeAudience(request.targetAudience());
        boolean isScheduled = request.scheduledAt() != null && request.scheduledAt().isAfter(Instant.now());

        NotificationDeliveryStatus status = isScheduled ? NotificationDeliveryStatus.PENDING : NotificationDeliveryStatus.SENT;

        NotificationBroadcast broadcast = NotificationBroadcast.create(
                request.title(),
                request.body(),
                canonicalAudience,
                request.actionUrl(),
                request.scheduledAt(),
                0,
                status
        );

        if (broadcastRepository != null) {
            broadcast = broadcastRepository.save(broadcast);
        }

        if (!isScheduled) {
            int count = dispatchBroadcastToAudience(canonicalAudience, request.title(), request.body(), request.actionUrl());
            broadcast.markSent(count);
            if (broadcastRepository != null) {
                broadcast = broadcastRepository.save(broadcast);
            }
        }

        return NotificationMapper.toBroadcastResponse(broadcast);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationBroadcastResponseDto> getBroadcastHistory(String audience, int page, int size) {
        if (broadcastRepository == null) {
            return new PageResult<>(List.of(), new PaginationMeta(0, size, 0, 0));
        }
        String normalized = audience != null && !audience.isBlank() ? normalizeAudience(audience) : null;
        var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationBroadcast> result = broadcastRepository.findByAudienceFilter(normalized, pageable);
        List<NotificationBroadcastResponseDto> items = result.getContent().stream()
                .map(NotificationMapper::toBroadcastResponse)
                .toList();
        return new PageResult<>(items, new PaginationMeta(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    @Autowired(required = false)
    private com.foodie.delivery.repository.DeliveryPartnerRepository deliveryPartnerRepository;

    private int dispatchBroadcastToAudience(String audience, String title, String body, String actionUrl) {
        if (userCredentialRepository == null) {
            return 0;
        }

        java.util.Set<UserCredential> targetUserSet = new java.util.LinkedHashSet<>();

        switch (audience) {
            case "CUSTOMER" -> targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.CUSTOMER));
            case "RESTAURANT" -> {
                targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.RESTAURANT));
                if (restaurantRepository != null) {
                    for (var r : restaurantRepository.findAll()) {
                        if (r.getOwnerUserCredentialId() != null) {
                            userCredentialRepository.findById(r.getOwnerUserCredentialId())
                                    .ifPresent(targetUserSet::add);
                        }
                    }
                }
            }
            case "DELIVERY_PARTNER" -> {
                targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.DELIVERY_PARTNER));
                if (deliveryPartnerRepository != null) {
                    for (var dp : deliveryPartnerRepository.findAll()) {
                        if (dp.getUserCredentialId() != null) {
                            userCredentialRepository.findById(dp.getUserCredentialId())
                                    .ifPresent(targetUserSet::add);
                        }
                    }
                }
            }
            default -> {
                targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.CUSTOMER));
                targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.RESTAURANT));
                targetUserSet.addAll(userCredentialRepository.findByUserType(UserType.DELIVERY_PARTNER));
                if (restaurantRepository != null) {
                    for (var r : restaurantRepository.findAll()) {
                        if (r.getOwnerUserCredentialId() != null) {
                            userCredentialRepository.findById(r.getOwnerUserCredentialId())
                                    .ifPresent(targetUserSet::add);
                        }
                    }
                }
                if (deliveryPartnerRepository != null) {
                    for (var dp : deliveryPartnerRepository.findAll()) {
                        if (dp.getUserCredentialId() != null) {
                            userCredentialRepository.findById(dp.getUserCredentialId())
                                    .ifPresent(targetUserSet::add);
                        }
                    }
                }
            }
        }

        UUID defaultTemplateId = templateRepository != null
                ? templateRepository.findAll().stream().map(NotificationTemplate::getId).findFirst().orElse(null)
                : null;

        int sentCount = 0;
        for (UserCredential uc : targetUserSet) {
            if (uc == null || !uc.isActive()) continue;
            if (logRepository.existsByUserCredentialIdAndTitleAndBody(uc.getId(), title, body)) {
                sentCount++;
                continue;
            }
            NotificationLog entry = NotificationLog.createBroadcastLog(
                    uc.getId(),
                    defaultTemplateId,
                    title,
                    body,
                    actionUrl,
                    audience,
                    NotificationDeliveryStatus.SENT
            );
            entry = logRepository.save(entry);
            publishDispatched(entry);
            sentCount++;
        }
        return sentCount;
    }

    private static String normalizeAudience(String audience) {
        if (audience == null) return "ALL";
        String upper = audience.trim().toUpperCase();
        if (upper.startsWith("CUST")) return "CUSTOMER";
        if (upper.startsWith("REST")) return "RESTAURANT";
        if (upper.startsWith("DELIV") || upper.startsWith("DRIVER")) return "DELIVERY_PARTNER";
        return "ALL";
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationResponseDto> list(
            UUID userCredentialId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<NotificationLog> result = unreadOnly
                ? logRepository.findByUserCredentialIdAndReadAtIsNull(userCredentialId, pageable)
                : logRepository.findByUserCredentialId(userCredentialId, pageable);
        List<NotificationResponseDto> items = result.getContent().stream()
                .map(NotificationMapper::toResponse)
                .toList();
        return new PageResult<>(items, new PaginationMeta(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userCredentialId) {
        return logRepository.countByUserCredentialIdAndReadAtIsNull(userCredentialId);
    }

    @Override
    @Transactional
    public NotificationReadResponseDto markRead(UUID userCredentialId, UUID notificationLogId) {
        NotificationLog entry = logRepository.findByIdAndUserCredentialId(notificationLogId, userCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        entry.markRead();
        return NotificationMapper.toReadResponse(entry);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userCredentialId) {
        return logRepository.markAllAsRead(userCredentialId);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponseDto getPreferences(UUID userCredentialId) {
        return preferenceStore.get(userCredentialId);
    }

    @Override
    public NotificationPreferenceResponseDto updatePreferences(
            UUID userCredentialId, UpdateNotificationPreferenceRequestDto request) {
        return preferenceStore.save(userCredentialId, request.pushEnabled(), request.smsEnabled());
    }

    @Override
    public void registerDeviceToken(UUID userCredentialId, String deviceToken) {
        deviceTokenStore.save(userCredentialId, deviceToken);
    }

    private void publishDispatched(NotificationLog entry) {
        eventPublisher.publishEvent(NotificationDispatchedEvent.of(
                entry.getUserCredentialId(),
                entry.getId(),
                entry.getTitle(),
                entry.getBody(),
                entry.getSentAt()));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000)
    @Transactional
    public void processScheduledNotifications() {
        if (broadcastRepository == null) return;
        List<NotificationBroadcast> pending = broadcastRepository
                .findByDeliveryStatusAndScheduledAtLessThanEqual(
                        NotificationDeliveryStatus.PENDING, Instant.now());
        for (NotificationBroadcast broadcast : pending) {
            int count = dispatchBroadcastToAudience(
                    broadcast.getTargetAudience(),
                    broadcast.getTitle(),
                    broadcast.getBody(),
                    broadcast.getActionUrl()
            );
            broadcast.markSent(count);
            broadcastRepository.save(broadcast);
            log.info("Processed scheduled notification broadcast id={} sent to {} recipients", broadcast.getId(), count);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupOldNotifications() {
        int deleted = logRepository
                .deleteOlderThan(Instant.now().minus(30, ChronoUnit.DAYS));
        log.info("Cleaned up {} old notifications older than 30 days", deleted);
    }
}
