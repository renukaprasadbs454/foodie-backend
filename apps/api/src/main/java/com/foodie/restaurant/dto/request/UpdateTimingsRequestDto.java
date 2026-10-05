package com.foodie.restaurant.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

public record UpdateTimingsRequestDto(
        @NotNull(message = "Open time is required")
        LocalTime openTime,
        
        @NotNull(message = "Close time is required")
        LocalTime closeTime,
        
        @NotEmpty(message = "At least one open day is required")
        List<String> openDays
) {}
