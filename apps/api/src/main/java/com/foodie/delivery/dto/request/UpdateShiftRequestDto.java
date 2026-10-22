package com.foodie.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateShiftRequestDto(
        @NotBlank(message = "Shift name is required.")
        String shiftName,

        @NotBlank(message = "Start time is required.")
        String startTime,

        @NotBlank(message = "End time is required.")
        String endTime,

        String status,

        String zoneId,

        String zoneName
) {
    public UpdateShiftRequestDto(String shiftName, String startTime, String endTime, String status) {
        this(shiftName, startTime, endTime, status, null, null);
    }
}
