package com.foodie.delivery.entity;

import com.foodie.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "delivery_shift")
public class DeliveryShift extends BaseEntity {

    @Column(name = "shift_name", nullable = false, length = 100)
    private String shiftName;

    @Column(name = "start_time", nullable = false, length = 50)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 50)
    private String endTime;

    @Column(name = "duration_hours", length = 20)
    private String durationHours;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    protected DeliveryShift() {}

    public DeliveryShift(String shiftName, String startTime, String endTime, String durationHours, String status) {
        this.shiftName = shiftName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationHours = durationHours;
        this.status = (status != null && !status.isBlank()) ? status : "ACTIVE";
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
}
