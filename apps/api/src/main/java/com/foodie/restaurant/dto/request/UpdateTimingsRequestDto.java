package com.foodie.restaurant.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.foodie.config.FlexibleLocalTimeDeserializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

public record UpdateTimingsRequestDto(
        @NotNull(message = "Open time is required")
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        LocalTime openTime,
        
        @NotNull(message = "Close time is required")
        @JsonDeserialize(using = FlexibleLocalTimeDeserializer.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        LocalTime closeTime,
        
        @NotEmpty(message = "At least one open day is required")
        List<String> openDays
) {}

