package com.foodie.infrastructure.maps.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.infrastructure.maps.dto.response.CoordinateValidationResponseDto;
import com.foodie.infrastructure.maps.dto.response.GeocodeResponseDto;
import com.foodie.infrastructure.maps.dto.response.ReverseGeocodeResponseDto;
import com.foodie.infrastructure.maps.service.MapsService;
import com.foodie.infrastructure.maps.util.CoordinateValidator;
import com.foodie.security.ratelimit.RedisRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps")
@Tag(name = "Maps")
public class MapsController {

    private final MapsService mapsService;
    private final RedisRateLimiter redisRateLimiter;

    public MapsController(
            MapsService mapsService,
            @Autowired(required = false) RedisRateLimiter redisRateLimiter
    ) {
        this.mapsService = mapsService;
        this.redisRateLimiter = redisRateLimiter;
    }

    @GetMapping("/geocode")
    @Operation(summary = "Forward geocode human-readable address to latitude and longitude")
    public ResponseEntity<ApiResponse<GeocodeResponseDto>> geocode(
            @RequestParam String address,
            HttpServletRequest request
    ) {
        enforceRateLimit(request, "geocode");
        return ResponseEntity.ok(ApiResponse.success(mapsService.geocode(address)));
    }

    @GetMapping("/reverse-geocode")
    @Operation(summary = "Reverse geocode latitude and longitude to human-readable address")
    public ResponseEntity<ApiResponse<ReverseGeocodeResponseDto>> reverseGeocode(
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            HttpServletRequest request
    ) {
        enforceRateLimit(request, "reverse-geocode");
        CoordinateValidator.validate(latitude, longitude);
        return ResponseEntity.ok(ApiResponse.success(mapsService.reverseGeocode(latitude, longitude)));
    }

    @GetMapping("/validate-coordinate")
    @Operation(summary = "Validate latitude and longitude coordinates")
    public ResponseEntity<ApiResponse<CoordinateValidationResponseDto>> validateCoordinate(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude
    ) {
        boolean valid = CoordinateValidator.isValid(latitude, longitude);
        String message = valid
                ? "Coordinates are valid."
                : "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180, and non-null/non-NaN.";

        return ResponseEntity.ok(ApiResponse.success(
                new CoordinateValidationResponseDto(valid, latitude, longitude, message)
        ));
    }

    private void enforceRateLimit(HttpServletRequest request, String action) {
        if (redisRateLimiter != null) {
            String clientIp = getClientIp(request);
            String rateKey = "ratelimit:maps:" + action + ":" + clientIp;
            redisRateLimiter.check(rateKey, 60, Duration.ofMinutes(1));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
