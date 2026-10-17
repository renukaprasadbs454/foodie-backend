package com.foodie.delivery.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_shift_booking")
public class DeliveryShiftBooking extends BaseEntity {

    @Column(name = "delivery_partner_id", nullable = false)
    private UUID deliveryPartnerId;

    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "start_time", nullable = false, length = 50)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 50)
    private String endTime;

    @Column(name = "duration_hours", length = 20)
    private String durationHours;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "BOOKED";

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    protected DeliveryShiftBooking() {}

    public DeliveryShiftBooking(
            UUID deliveryPartnerId,
            UUID shiftId,
            String shiftName,
            String startTime,
            String endTime,
            String durationHours,
            String status,
            Instant bookedAt
    ) {
        this.deliveryPartnerId = deliveryPartnerId;
        this.shiftId = shiftId;
        this.shiftName = shiftName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationHours = durationHours;
        this.status = (status != null && !status.isBlank()) ? status : "BOOKED";
        this.bookedAt = (bookedAt != null) ? bookedAt : Instant.now();
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

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getDurationHours() {
        return durationHours;
    }

    public void setDurationHours(String durationHours) {
        this.durationHours = durationHours;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }

    public void setBookedAt(Instant bookedAt) {
        this.bookedAt = bookedAt;
    }
}
