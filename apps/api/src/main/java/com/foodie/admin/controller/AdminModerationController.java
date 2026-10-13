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

    public AdminModerationController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "List all platform reviews for admin dashboard")
    public ResponseEntity<ApiResponse<List<AdminReviewItemDto>>> listReviews(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort
    ) {
        var result = adminOperationsService.listReviews(principal.userId(), storeName, minRating, page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.pagination()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.can(authentication, 'REVIEW', 'MODERATE')")
    @Operation(summary = "Get review compliance stats")
    public ResponseEntity<ApiResponse<AdminReviewStatsDto>> getStats(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminOperationsService.getReviewStats(principal.userId())));
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
