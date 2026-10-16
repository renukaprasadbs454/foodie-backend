package com.foodie.admin.controller;

import com.foodie.admin.service.AdminOperationsService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.coupon.dto.request.CreateCouponRequestDto;
import com.foodie.coupon.dto.response.CouponResponseDto;
import com.foodie.coupon.dto.response.DeactivateCouponResponseDto;
import com.foodie.coupon.repository.CouponRepository;
import com.foodie.coupon.mapper.CouponMapper;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin coupon HTTP (API Contracts MODULE 13.4 / 13.5).
 * Delegates mutations to CouponAdminService; Admin owns audit_log writes.
 */
@RestController
@RequestMapping("/api/v1/admin/coupons")
@Tag(name = "Admin — Coupon")
public class AdminCouponController {

        private final AdminOperationsService adminOperationsService;
        private final CouponRepository couponRepository;

        public AdminCouponController(AdminOperationsService adminOperationsService, CouponRepository couponRepository) {
                this.adminOperationsService = adminOperationsService;
                this.couponRepository = couponRepository;
        }

        @org.springframework.beans.factory.annotation.Autowired(required = false)
        private com.foodie.admin.security.AdminAccess adminAccess;
        @org.springframework.beans.factory.annotation.Autowired(required = false)
        private com.foodie.admin.service.AdminZoneService adminZoneService;
        @org.springframework.beans.factory.annotation.Autowired(required = false)
        private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

        @GetMapping
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Get all coupons")
        public ResponseEntity<ApiResponse<List<CouponResponseDto>>> getAllCoupons(
                        org.springframework.security.core.Authentication authentication,
                        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @org.springframework.web.bind.annotation.RequestParam(value = "zoneId", required = false) String zoneIdParam) {
                String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                                ? activeZoneHeader.trim()
                                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

                String activeZoneId = adminAccess != null
                                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                                : requestedZoneId;

                var list = couponRepository.findAll();
                if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL") && adminZoneService != null && restaurantRepository != null) {
                        com.foodie.admin.entity.LocationZone zone = adminZoneService.validateZone(activeZoneId);
                        list = list.stream().filter(c -> {
                                if (c.getRestaurantId() == null) return true; // platform coupon
                                var r = restaurantRepository.findById(c.getRestaurantId()).orElse(null);
                                if (r == null) return false;
                                return adminZoneService.isLocationInZone(
                                                r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                                                r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                                                null,
                                                zone);
                        }).toList();
                }

                return ResponseEntity.ok(ApiResponse.success(
                                list.stream()
                                                .map(CouponMapper::toResponse)
                                                .collect(Collectors.toList())));
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Create a coupon")
        public ResponseEntity<ApiResponse<CouponResponseDto>> create(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @Valid @RequestBody CreateCouponRequestDto request) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                adminOperationsService.createCoupon(principal.userId(), request)));
        }

        @PatchMapping("/{id}/deactivate")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Deactivate a coupon")
        public ResponseEntity<ApiResponse<DeactivateCouponResponseDto>> deactivate(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.deactivateCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/activate")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Activate a coupon")
        public ResponseEntity<ApiResponse<DeactivateCouponResponseDto>> activate(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.activateCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/approve")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Approve a pending restaurant campaign")
        public ResponseEntity<ApiResponse<CouponResponseDto>> approve(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.approveCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/reject")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Reject a pending restaurant campaign")
        public ResponseEntity<ApiResponse<CouponResponseDto>> reject(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.rejectCoupon(principal.userId(), couponId)));
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN', 'OPS', 'FINANCE')")
        @Operation(summary = "Delete a coupon permanently")
        public ResponseEntity<ApiResponse<Boolean>> deleteCoupon(
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.deleteCoupon(principal.userId(), couponId)));
        }
}
