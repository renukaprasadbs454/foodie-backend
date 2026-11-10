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

    private boolean isRestaurantCoupon(Coupon c) {
        if (c == null) return false;
        if (c.getCouponType() == Coupon.CouponType.PROMO_COUPON) {
            return false;
        }
        if (c.getCouponType() == Coupon.CouponType.GENERIC && c.getFunderType() == Coupon.FunderType.FOODIE && c.getRestaurantId() == null) {
            return false;
        }
        return c.getRestaurantId() != null ||
               c.getFunderType() == Coupon.FunderType.RESTAURANT ||
               c.getFunderType() == Coupon.FunderType.MERCHANT ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT_COUPON ||
               c.getCouponType() == Coupon.CouponType.RESTAURANT_FIRST_ORDER;
    }

    @GetMapping
    @PreAuthorize("hasRole('RESTAURANT') or hasRole('ADMIN') or hasRole('CUSTOMER')")
    @Operation(summary = "Get all coupons assigned to this restaurant")
    public ResponseEntity<ApiResponse<List<CouponResponseDto>>> getRestaurantCoupons(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "restaurantId", required = false) UUID requestedRestaurantId) {
        UUID restId = resolveRestaurantId(principal, requestedRestaurantId);
        if (restId == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        List<Coupon> rawList = couponRepository.findByRestaurantId(restId).stream()
                .filter(this::isRestaurantCoupon)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        if (restaurantRepository != null) {
            restaurantRepository.findById(restId).ifPresent(r -> {
                String zid = r.getZoneId();
                String zname = r.getZoneName();
                if (zid != null && !zid.isBlank()) {
                    for (Coupon zc : couponRepository.findByZoneId(zid.trim())) {
                        if (isRestaurantCoupon(zc) && rawList.stream().noneMatch(c -> c.getId().equals(zc.getId()))) {
                            rawList.add(zc);
                        }
                    }
                }
                if (zname != null && !zname.isBlank()) {
                    for (Coupon zc : couponRepository.findByZoneId(zname.trim())) {
                        if (isRestaurantCoupon(zc) && rawList.stream().noneMatch(c -> c.getId().equals(zc.getId()))) {
                            rawList.add(zc);
                        }
                    }
                }
            });
        }
        // Also include All Locations / global coupons
        for (String allKey : java.util.List.of("ALL", "all", "ALL_LOCATIONS", "All Locations")) {
            for (Coupon ac : couponRepository.findByZoneId(allKey)) {
                if (isRestaurantCoupon(ac) && rawList.stream().noneMatch(c -> c.getId().equals(ac.getId()))) {
                    rawList.add(ac);
                }
            }
        }
        couponRepository.findAll().stream()
                .filter(c -> isRestaurantCoupon(c) && (c.getZoneId() == null || c.getZoneId().isBlank() || "ALL".equalsIgnoreCase(c.getZoneId()) || "All Locations".equalsIgnoreCase(c.getZoneId())))
                .forEach(c -> {
                    if (rawList.stream().noneMatch(existing -> existing.getId().equals(c.getId()))) {
                        rawList.add(c);
                    }
                });

        List<Coupon> coupons = rawList;

        List<CouponResponseDto> dtos = coupons.stream()
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
