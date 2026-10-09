package com.foodie.notification.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.NotificationDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
public class NotificationLog extends BaseEntity {

    @Column(name = "user_credential_id", nullable = false, updatable = false)
    private UUID userCredentialId;

    @Column(name = "template_id", nullable = true, updatable = false)
    private UUID templateId;

    @Column(name = "title", nullable = false, length = 255, updatable = false)
    private String title;

    @Column(name = "body", nullable = false, length = 500, updatable = false)
    private String body;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "target_audience", length = 50)
    private String targetAudience;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private NotificationDeliveryStatus deliveryStatus;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationLog() {
    }

    public static NotificationLog create(
            UUID userCredentialId,
            UUID templateId,
            String title,
            String body,
            NotificationDeliveryStatus status
    ) {
        NotificationLog log = new NotificationLog();
        log.userCredentialId = userCredentialId;
        log.templateId = templateId;
        log.title = title;
        log.body = body;
        log.sentAt = Instant.now();
        log.deliveryStatus = status;
        return log;
    }

    public static NotificationLog createBroadcastLog(
            UUID userCredentialId,
            UUID templateId,
            String title,
            String body,
            String actionUrl,
            String targetAudience,
            NotificationDeliveryStatus status
    ) {
        NotificationLog log = new NotificationLog();
        log.userCredentialId = userCredentialId;
        log.templateId = templateId;
        log.title = title;
        log.body = body;
        log.actionUrl = actionUrl;
        log.targetAudience = targetAudience;
        log.sentAt = Instant.now();
        log.deliveryStatus = status;
        return log;
    }

    public static NotificationLog createBroadcastLog(
            UUID userCredentialId,
            String title,
            String body,
            String actionUrl,
            String targetAudience,
            NotificationDeliveryStatus status
    ) {
        return createBroadcastLog(userCredentialId, null, title, body, actionUrl, targetAudience, status);
    }

    public void markDeliveryStatus(NotificationDeliveryStatus status) {
        this.deliveryStatus = status;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public UUID getUserCredentialId() {
        return userCredentialId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public NotificationDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
