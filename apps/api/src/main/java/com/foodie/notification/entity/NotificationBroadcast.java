package com.foodie.notification.entity;

import com.foodie.common.entity.BaseEntity;
import com.foodie.common.enums.NotificationDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification_broadcast")
public class NotificationBroadcast extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Column(name = "target_audience", nullable = false, length = 50)
    private String targetAudience;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "recipients_count", nullable = false)
    private int recipientsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private NotificationDeliveryStatus deliveryStatus;

    protected NotificationBroadcast() {
    }

    public static NotificationBroadcast create(
            String title,
            String body,
            String targetAudience,
            String actionUrl,
            Instant scheduledAt,
            int recipientsCount,
            NotificationDeliveryStatus status
    ) {
        NotificationBroadcast nb = new NotificationBroadcast();
        nb.title = title;
        nb.body = body;
        nb.targetAudience = targetAudience;
        nb.actionUrl = actionUrl;
        nb.scheduledAt = scheduledAt;
        nb.recipientsCount = recipientsCount;
        nb.deliveryStatus = status;
        if (status == NotificationDeliveryStatus.SENT) {
            nb.sentAt = Instant.now();
        }
        return nb;
    }

    public void markSent(int recipientsCount) {
        this.recipientsCount = recipientsCount;
        this.sentAt = Instant.now();
        this.deliveryStatus = NotificationDeliveryStatus.SENT;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getTargetAudience() {
        return targetAudience;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public int getRecipientsCount() {
        return recipientsCount;
    }

    public NotificationDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }
}
