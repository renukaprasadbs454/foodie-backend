package com.foodie.admin.controller;

import com.foodie.admin.dto.request.SuspendRestaurantRequestDto;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.admin.service.AdminZoneService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.restaurant.dto.response.RestaurantDetailResponseDto;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
@Tag(name = "Admin — Restaurant")
public class AdminRestaurantController {

    private final AdminOperationsService adminOperationsService;
    private final AdminZoneService adminZoneService;
    private final com.foodie.restaurant.service.RestaurantService restaurantService;

    public AdminRestaurantController(
            AdminOperationsService adminOperationsService,
            AdminZoneService adminZoneService,
            com.foodie.restaurant.service.RestaurantService restaurantService
    ) {
        this.adminOperationsService = adminOperationsService;
        this.adminZoneService = adminZoneService;
        this.restaurantService = restaurantService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List restaurants for admin, scoped to active zone if requested")
    public ResponseEntity<ApiResponse<AdminOperationsService.PageResult<RestaurantDetailResponseDto>>> list(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort) {

        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        String activeZoneId = adminAccess != null
                ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                : requestedZoneId;

        var result = adminOperationsService.listRestaurants(principal.userId(), status, page, size, sort);

        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
            LocationZone zone = adminZoneService.validateZone(activeZoneId);
            List<RestaurantDetailResponseDto> filteredItems = result.items().stream()
                    .filter(r -> {
                        if (r.zoneId() != null && r.zoneId().equalsIgnoreCase(zone.getId())) {
                            return true;
                        }
                        Double lat = r.latitude() != null ? r.latitude().doubleValue() : null;
                        Double lng = r.longitude() != null ? r.longitude().doubleValue() : null;
                        String city = r.address() != null ? r.address().city() : null;
                        return adminZoneService.isLocationInZone(lat, lng, city, zone);
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(
                    new AdminOperationsService.PageResult<>(filteredItems, result.pagination())));
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Get single restaurant details with documents and legal info for admin")
    public ResponseEntity<ApiResponse<RestaurantDetailResponseDto>> getById(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                restaurantService.getById(id, principal != null ? principal.userId() : null, true)));
    }

    private void assertRestaurantInActiveZone(org.springframework.security.core.Authentication authentication, UUID restaurantId, String activeZoneHeader, UUID actorCredentialId) {
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
        LocationZone zone = adminZoneService.validateZone(effectiveZone.trim());
        var res = adminOperationsService.listRestaurants(actorCredentialId, null, 0, 1000, "createdAt");
        var matched = res.items().stream()
                .filter(r -> r.restaurantId().equals(restaurantId))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            Double lat = matched.latitude() != null ? matched.latitude().doubleValue() : null;
            Double lng = matched.longitude() != null ? matched.longitude().doubleValue() : null;
            String city = matched.address() != null ? matched.address().city() : null;
            if (!adminZoneService.isLocationInZone(lat, lng, city, zone)) {
                throw new ForbiddenException("Restaurant is outside your active operating zone: " + zone.getZoneName());
            }
        }
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Approve a PENDING restaurant")
    public ResponseEntity<ApiResponse<RestaurantDetailResponseDto>> approve(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.approveRestaurant(principal.userId(), restaurantId)));
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Suspend a restaurant")
    public ResponseEntity<ApiResponse<RestaurantDetailResponseDto>> suspend(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId,
            @Valid @RequestBody SuspendRestaurantRequestDto request) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.suspendRestaurant(principal.userId(), restaurantId, request)));
    }

    @PatchMapping("/positions")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Update top positions of restaurants")
    public ResponseEntity<ApiResponse<Void>> updatePositions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody java.util.List<com.foodie.admin.dto.request.UpdateRestaurantPositionRequestDto> positions) {
        adminOperationsService.updateTopPositions(principal.userId(), positions);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Reject a PENDING restaurant onboarding submission")
    public ResponseEntity<ApiResponse<RestaurantDetailResponseDto>> reject(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId,
            @Valid @RequestBody SuspendRestaurantRequestDto request) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.rejectRestaurant(principal.userId(), restaurantId, request.reason())));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/{id}/documents/{docId}/verify", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Verify a restaurant document")
    public ResponseEntity<ApiResponse<com.foodie.restaurant.dto.response.RestaurantDocumentResponseDto>> verifyDocument(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId,
            @PathVariable("docId") UUID documentId) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.verifyRestaurantDocument(principal.userId(), restaurantId, documentId)));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/{id}/documents/{docId}/reject", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Reject a restaurant document")
    public ResponseEntity<ApiResponse<com.foodie.restaurant.dto.response.RestaurantDocumentResponseDto>> rejectDocument(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId,
            @PathVariable("docId") UUID documentId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Document rejected by admin.";
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.rejectRestaurantDocument(principal.userId(), restaurantId, documentId, reason)));
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = "/{id}/commission", method = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Update restaurant commission percentage")
    public ResponseEntity<ApiResponse<RestaurantDetailResponseDto>> updateCommission(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId,
            @RequestBody java.util.Map<String, Object> body) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        Object commObj = body != null ? body.get("commissionPct") : null;
        java.math.BigDecimal comm = commObj != null ? new java.math.BigDecimal(commObj.toString()) : new java.math.BigDecimal("15.00");
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.updateRestaurantCommission(principal.userId(), restaurantId, comm)));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Permanently delete a SUSPENDED restaurant")
    public ResponseEntity<ApiResponse<Void>> delete(
            org.springframework.security.core.Authentication authentication,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable("id") UUID restaurantId) {
        assertRestaurantInActiveZone(authentication, restaurantId, activeZoneHeader, principal.userId());
        adminOperationsService.deleteRestaurant(principal.userId(), restaurantId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
