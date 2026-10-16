package com.foodie.admin.controller;

import com.foodie.admin.dto.request.FlagReviewRequestDto;
import com.foodie.admin.dto.response.AdminReviewItemDto;
import com.foodie.admin.dto.response.AdminReviewStatsDto;
import com.foodie.admin.dto.response.ModerationResponseDto;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review moderation and admin listing ops (Module 13 scope).
 */
@RestController
@RequestMapping({"/api/v1/admin/reviews", "/api/admin/reviews"})
@Tag(name = "Admin - Moderation and Reviews")
public class AdminModerationController {

    private final AdminOperationsService adminOperationsService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.service.AdminZoneService adminZoneService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    public AdminModerationController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "List all platform reviews for admin dashboard")
    public ResponseEntity<ApiResponse<List<AdminReviewItemDto>>> listReviews(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort
    ) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        var result = adminOperationsService.listReviews(principal.userId(), storeName, minRating, page, size, sort);
        List<AdminReviewItemDto> items = result.items();

        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL") && adminZoneService != null && restaurantRepository != null) {
            com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(activeZoneId);
            items = items.stream().filter(item -> {
                if (item.restaurantId() != null) {
                    var r = restaurantRepository.findById(item.restaurantId()).orElse(null);
                    if (r != null) {
                        return adminZoneService.isLocationInZone(
                                r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                                r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                                null,
                                zone);
                    }
                }
                return true;
            }).collect(java.util.stream.Collectors.toList());
        }

        return ResponseEntity.ok(ApiResponse.success(items, result.pagination()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "Get review compliance stats")
    public ResponseEntity<ApiResponse<AdminReviewStatsDto>> getStats(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam
    ) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        var stats = adminOperationsService.getReviewStats(principal.userId());
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL") && adminZoneService != null && restaurantRepository != null) {
            com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(activeZoneId);
            var result = adminOperationsService.listReviews(principal.userId(), null, null, 0, 1000, null);
            long total = 0;
            long flagged = 0;
            for (var item : result.items()) {
                if (item.restaurantId() != null) {
                    var r = restaurantRepository.findById(item.restaurantId()).orElse(null);
                    if (r != null && adminZoneService.isLocationInZone(
                            r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                            r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                            null,
                            zone)) {
                        total++;
                        if (item.isReported() || "FLAGGED".equalsIgnoreCase(item.status())) {
                            flagged++;
                        }
                    }
                }
            }
            stats = new AdminReviewStatsDto(total, flagged, stats.auditLogs(), stats.resolvedIssues());
        }

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping("/{id}/flag")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "Flag a review for moderation (hides from public list)")
    public ResponseEntity<ApiResponse<ModerationResponseDto>> flag(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID reviewId,
            @Valid @RequestBody FlagReviewRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.flagReview(principal.userId(), reviewId, request)));
    }

    @DeleteMapping("/{id}/flag")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "Clear a review moderation flag")
    public ResponseEntity<ApiResponse<ModerationResponseDto>> clearFlag(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID reviewId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.clearReviewFlag(principal.userId(), reviewId)));
    }
}
