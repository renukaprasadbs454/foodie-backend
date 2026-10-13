package com.foodie.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ZoneSelectionRequestDto(
        @NotBlank(message = "zoneId is required")
        String zoneId
) {}
