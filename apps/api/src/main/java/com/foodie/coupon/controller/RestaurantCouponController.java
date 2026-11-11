package com.foodie.coupon.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.coupon.dto.request.CreateCouponRequestDto;
import com.foodie.coupon.dto.response.CouponResponseDto;
import com.foodie.coupon.entity.Coupon;
import com.foodie.coupon.mapper.CouponMapper;
import com.foodie.coupon.repository.CouponRepository;
import com.foodie.coupon.service.impl.CouponServiceImpl;
import com.foodie.security.principal.AuthPrincipal;
import com.foodie.shared.contract.RestaurantSummaryProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurant/coupons")
@Tag(name = "Restaurant 🏷️ Campaign")
@Validated
public class RestaurantCouponController {

    private final CouponServiceImpl couponService;
    private final CouponRepository couponRepository;
    private final RestaurantSummaryProvider restaurantSummaryProvider;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.restaurant.repository.RestaurantRepository restaurantRepository;

    public RestaurantCouponController(
            CouponServiceImpl couponService,
            CouponRepository couponRepository,
            RestaurantSummaryProvider restaurantSummaryProvider) {
        this.couponService = couponService;
        this.couponRepository = couponRepository;
        this.restaurantSummaryProvider = restaurantSummaryProvider;
    }

    private UUID resolveRestaurantId(AuthPrincipal principal, UUID requestedRestId) {
        if (requestedRestId != null) {
            return requestedRestId;
        }
        if (principal != null && principal.userId() != null) {
            return restaurantSummaryProvider.findByOwnerUserCredentialId(principal.userId())
                    .map(RestaurantSummaryProvider.RestaurantSummary::restaurantId)
                    .orElse(null);
        }
        return null;
    }

    private boolean isZoneMatch(String zoneA, String zoneB) {
        if (zoneA == null || zoneB == null) return false;
        String a = zoneA.trim().toLowerCase();
        String b = zoneB.trim().toLowerCase();
        if (a.equals(b)) return true;
        if (a.equals("all") || b.equals("all") || a.equals("all_locations") || b.equals("all_locations")) return true;
        String normA = a.replace("dz-", "").replace("-zone", "").replace(" zone", "").trim();
        String normB = b.replace("dz-", "").replace("-zone", "").replace(" zone", "").trim();
        if (!normA.isEmpty() && normA.equals(normB)) return true;
        if ((normA.contains("bangalore") || normA.contains("bengaluru") || normA.equals("blr")) &&
            (normB.contains("bangalore") || normB.contains("bengaluru") || normB.equals("blr"))) return true;
        if ((normA.contains("tumkur") || normA.contains("tumakuru") || normA.equals("tmk")) &&
            (normB.contains("tumkur") || normB.contains("tumakuru") || normB.equals("tmk"))) return true;
        if ((normA.contains("hubli") || normA.contains("hubballi") || normA.equals("hbl")) &&
            (normB.contains("hubli") || normB.contains("hubballi") || normB.equals("hbl"))) return true;
        return false;
    }

    private boolean isRestaurantCoupon(Coupon c) {
        if (c == null) return false;
        if (c.getCouponType() == Coupon.CouponType.PROMO_COUPON) {
            return false;
        }
        if (c.getCouponType() == Coupon.CouponType.GENERIC && c.getFunderType() == Coupon.FunderType.FOODIE && c.getRestaurantId() == null) {
            return false;
        }
        return c.getRestaurantId() != null ||
               c.getFunderType() == Coupon.FunderType.SHARED ||
               c.getFunderType() == Coupon.FunderType.RESTAURANT ||
               c.getFunderType() == Coupon.FunderType.MERCHANT ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT_COUPON ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT_FIRST_ORDER;
    }

    @GetMapping
    @PreAuthorize("hasRole('RESTAURANT') or hasRole('ADMIN') or hasRole('CUSTOMER') or isAnonymous() or isAuthenticated()")
    @Operation(summary = "Get all coupons assigned to this restaurant")
    public ResponseEntity<ApiResponse<List<CouponResponseDto>>> getRestaurantCoupons(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "restaurantId", required = false) UUID requestedRestaurantId,
            @RequestParam(value = "zoneId", required = false) String requestedZoneId) {
        UUID restId = resolveRestaurantId(principal, requestedRestaurantId);
        if (restId == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }

        String restZoneId = null;
        String restZoneName = null;
        if (restaurantRepository != null) {
            var rOpt = restaurantRepository.findById(restId);
            if (rOpt.isPresent()) {
                restZoneId = rOpt.get().getZoneId();
                restZoneName = rOpt.get().getZoneName();
            }
        }

        final String finalRestZoneId = restZoneId;
        final String finalRestZoneName = restZoneName;
        final String finalRequestedZoneId = requestedZoneId;

        List<Coupon> allCoupons = couponRepository.findAll();
        List<Coupon> matchingCoupons = new java.util.ArrayList<>();

        for (Coupon c : allCoupons) {
            if (c == null || !isRestaurantCoupon(c)) {
                continue;
            }

            // 1. Direct assignment to this restaurant
            if (c.getRestaurantId() != null && c.getRestaurantId().equals(restId)) {
                matchingCoupons.add(c);
                continue;
            }

            // If coupon is assigned to a DIFFERENT restaurant, skip
            if (c.getRestaurantId() != null && !c.getRestaurantId().equals(restId)) {
                continue;
            }

            // 2. All Locations / Global restaurant campaigns
            String cZone = c.getZoneId();
            if (cZone == null || cZone.isBlank() || isZoneMatch(cZone, "ALL")) {
                matchingCoupons.add(c);
                continue;
            }

            // 3. Zone matching with restaurant profile or requested zone
            if (finalRestZoneId != null && isZoneMatch(cZone, finalRestZoneId)) {
                matchingCoupons.add(c);
                continue;
            }
            if (finalRestZoneName != null && isZoneMatch(cZone, finalRestZoneName)) {
                matchingCoupons.add(c);
                continue;
            }
            if (finalRequestedZoneId != null && isZoneMatch(cZone, finalRequestedZoneId)) {
                matchingCoupons.add(c);
                continue;
            }
        }

        List<CouponResponseDto> dtos = matchingCoupons.stream()
                .map(c -> {
                    CouponResponseDto dto = CouponMapper.toResponse(c);
                    if (dto.restaurantId() == null) {
                        return new CouponResponseDto(
                                dto.couponId(),
                                dto.code(),
                                dto.discountType(),
                                dto.funderType(),
                                dto.couponType(),
                                dto.benefitMode(),
                                dto.approvalStatus(),
                                dto.value(),
                                dto.minOrderAmount(),
                                dto.maxDiscountAmount(),
                                dto.expiryDate(),
                                dto.usageLimitTotal(),
                                dto.usageLimitPerUser(),
                                restId,
                                dto.isActive(),
                                dto.zoneId(),
                                dto.adminDiscount(),
                                dto.restaurantDiscount()
                        );
                    }
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT')")
    @Operation(summary = "Request a new campaign/coupon (Pending Approval)")
    public ResponseEntity<ApiResponse<CouponResponseDto>> createCampaign(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateCouponRequestDto request) {
        CouponResponseDto response = couponService.createForRestaurant(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RESTAURANT') or hasRole('ADMIN')")
    @Operation(summary = "Enable or disable a restaurant coupon")
    public ResponseEntity<ApiResponse<CouponResponseDto>> toggleStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID couponId,
            @RequestParam("isActive") boolean isActive) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found."));

        UUID restId = resolveRestaurantId(principal, null);
        if (restId != null && coupon.getRestaurantId() != null && !restId.equals(coupon.getRestaurantId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Not authorized to manage coupons for another restaurant.");
        }

        if (isActive) {
            coupon.activate();
        } else {
            coupon.deactivate();
        }
        couponRepository.save(coupon);

        return ResponseEntity.ok(ApiResponse.success(CouponMapper.toResponse(coupon)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('RESTAURANT') or hasRole('ADMIN')")
    @Operation(summary = "Activate a restaurant coupon")
    public ResponseEntity<ApiResponse<CouponResponseDto>> activate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID couponId) {
        return toggleStatus(principal, couponId, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('RESTAURANT') or hasRole('ADMIN')")
    @Operation(summary = "Deactivate a restaurant coupon")
    public ResponseEntity<ApiResponse<CouponResponseDto>> deactivate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID couponId) {
        return toggleStatus(principal, couponId, false);
    }
}
