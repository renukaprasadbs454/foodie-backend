package com.foodie.delivery.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_online_session")
public class DeliveryOnlineSession extends BaseEntity {

    @Column(name = "delivery_partner_id", nullable = false)
    private UUID deliveryPartnerId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "online_at", nullable = false)
    private Instant onlineAt;

    @Column(name = "offline_at")
    private Instant offlineAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    protected DeliveryOnlineSession() {}

    public DeliveryOnlineSession(UUID deliveryPartnerId, Instant onlineAt) {
        this.deliveryPartnerId = deliveryPartnerId;
        this.onlineAt = (onlineAt != null) ? onlineAt : Instant.now();
    }

    public DeliveryOnlineSession(UUID deliveryPartnerId, UUID shiftId, Instant onlineAt) {
        this.deliveryPartnerId = deliveryPartnerId;
        this.shiftId = shiftId;
        this.onlineAt = (onlineAt != null) ? onlineAt : Instant.now();
    }

    public UUID getDeliveryPartnerId() {
        return deliveryPartnerId;
    }

    public void setDeliveryPartnerId(UUID deliveryPartnerId) {
        this.deliveryPartnerId = deliveryPartnerId;
    }

    public UUID getShiftId() {
        return shiftId;
    }

    public void setShiftId(UUID shiftId) {
        this.shiftId = shiftId;
    }

    public Instant getOnlineAt() {
        return onlineAt;
    }

    public void setOnlineAt(Instant onlineAt) {
        this.onlineAt = onlineAt;
    }

    public Instant getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(Instant offlineAt) {
        this.offlineAt = offlineAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
