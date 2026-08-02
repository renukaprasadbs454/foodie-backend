package com.foodie.coupon.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.coupon.dto.request.CreateCouponRequestDto;
import com.foodie.coupon.dto.response.CouponResponseDto;
import com.foodie.coupon.dto.response.DeactivateCouponResponseDto;
import com.foodie.coupon.service.CouponAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin coupon mutations live on Coupon-owned tables (API Contracts MODULE 13.4 / 13.5).
 * Exposed here so Admin can call Coupon service methods without writing Coupon tables directly.
 * audit_log persistence awaits the Admin module.
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@Tag(name = "Admin — Coupon")
public class AdminCouponController {

    private final CouponAdminService couponAdminService;

    public AdminCouponController(CouponAdminService couponAdminService) {
        this.couponAdminService = couponAdminService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a coupon")
    public ResponseEntity<ApiResponse<CouponResponseDto>> create(
            @Valid @RequestBody CreateCouponRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(couponAdminService.create(request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a coupon")
    public ResponseEntity<ApiResponse<DeactivateCouponResponseDto>> deactivate(
            @PathVariable("id") UUID couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponAdminService.deactivate(couponId)));
    }
}
