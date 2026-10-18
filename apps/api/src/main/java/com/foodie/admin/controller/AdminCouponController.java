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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
                if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
                        com.foodie.admin.entity.LocationZone zone = adminZoneService != null ? adminZoneService.validateZone(activeZoneId) : null;
                        list = list.stream().filter(c -> {
                                if (c.getZoneId() != null && !c.getZoneId().isBlank()) {
                                        return c.getZoneId().trim().equalsIgnoreCase(activeZoneId.trim());
                                }
                                if (c.getRestaurantId() != null && restaurantRepository != null && adminZoneService != null && zone != null) {
                                        var r = restaurantRepository.findById(c.getRestaurantId()).orElse(null);
                                        if (r == null) return false;
                                        return adminZoneService.isLocationInZone(
                                                        r.getLatitude() != null ? r.getLatitude().doubleValue() : null,
                                                        r.getLongitude() != null ? r.getLongitude().doubleValue() : null,
                                                        null,
                                                        zone);
                                }
                                return false; // Zone admin only sees their particular zone coupons
                        }).toList();
                }

                return ResponseEntity.ok(ApiResponse.success(
                                list.stream()
                                                .map(CouponMapper::toResponse)
                                                .collect(Collectors.toList())));
        }

        private void assertCouponInZone(org.springframework.security.core.Authentication authentication, UUID couponId, String activeZoneHeader) {
                String effectiveZone = activeZoneHeader;
                if (adminAccess != null && authentication != null && authentication.isAuthenticated()
                                && authentication.getPrincipal() instanceof com.foodie.security.principal.AuthPrincipal) {
                        com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
                        if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN
                                        && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
                                effectiveZone = admin.getAssignedZoneId();
                        }
                }
                if (effectiveZone == null || effectiveZone.isBlank() || effectiveZone.equalsIgnoreCase("ALL")) {
                        return;
                }
                var c = couponRepository.findById(couponId).orElse(null);
                if (c != null && c.getZoneId() != null && !c.getZoneId().isBlank()) {
                        if (!c.getZoneId().trim().equalsIgnoreCase(effectiveZone.trim())) {
                                throw new com.foodie.common.exception.ForbiddenException("Cross-zone access denied: Coupon belongs to zone " + c.getZoneId());
                        }
                }
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Create a coupon")
        public ResponseEntity<ApiResponse<CouponResponseDto>> create(
                        org.springframework.security.core.Authentication authentication,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @Valid @RequestBody CreateCouponRequestDto request) {
                String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                                ? activeZoneHeader.trim()
                                : (request.getZoneId() != null && !request.getZoneId().isBlank() ? request.getZoneId().trim() : null);

                String activeZoneId = adminAccess != null
                                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                                : requestedZoneId;

                if (adminAccess != null && authentication != null) {
                        com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
                        if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN
                                        && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
                                request.setZoneId(admin.getAssignedZoneId());
                        } else if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
                                request.setZoneId(activeZoneId);
                        }
                }

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                adminOperationsService.createCoupon(principal.userId(), request)));
        }

        @PatchMapping("/{id}/deactivate")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Deactivate a coupon")
        public ResponseEntity<ApiResponse<DeactivateCouponResponseDto>> deactivate(
                        org.springframework.security.core.Authentication authentication,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                assertCouponInZone(authentication, couponId, activeZoneHeader);
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.deactivateCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/activate")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Activate a coupon")
        public ResponseEntity<ApiResponse<DeactivateCouponResponseDto>> activate(
                        org.springframework.security.core.Authentication authentication,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                assertCouponInZone(authentication, couponId, activeZoneHeader);
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.activateCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/approve")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Approve a pending restaurant campaign")
        public ResponseEntity<ApiResponse<CouponResponseDto>> approve(
                        org.springframework.security.core.Authentication authentication,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                assertCouponInZone(authentication, couponId, activeZoneHeader);
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.approveCoupon(principal.userId(), couponId)));
        }

        @PatchMapping("/{id}/reject")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'FINANCE', 'SUPER_ADMIN')")
        @Operation(summary = "Reject a pending restaurant campaign")
        public ResponseEntity<ApiResponse<CouponResponseDto>> reject(
                        org.springframework.security.core.Authentication authentication,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                assertCouponInZone(authentication, couponId, activeZoneHeader);
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.rejectCoupon(principal.userId(), couponId)));
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN', 'OPS', 'FINANCE')")
        @Operation(summary = "Delete a coupon permanently")
        public ResponseEntity<ApiResponse<Boolean>> deleteCoupon(
                        org.springframework.security.core.Authentication authentication,
                        @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
                        @AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable("id") UUID couponId) {
                assertCouponInZone(authentication, couponId, activeZoneHeader);
                return ResponseEntity.ok(ApiResponse.success(
                                adminOperationsService.deleteCoupon(principal.userId(), couponId)));
        }
}
