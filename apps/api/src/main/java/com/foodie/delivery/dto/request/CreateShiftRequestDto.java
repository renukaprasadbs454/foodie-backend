package com.foodie.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateShiftRequestDto(
        @NotBlank(message = "Shift name is required.")
        String shiftName,

        @NotBlank(message = "Start time is required.")
        String startTime,

        @NotBlank(message = "End time is required.")
        String endTime
) {}
