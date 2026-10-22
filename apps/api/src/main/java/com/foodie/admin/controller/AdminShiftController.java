package com.foodie.admin.controller;

import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.security.AdminAccess;
import com.foodie.common.dto.ApiResponse;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.request.UpdateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.entity.DeliveryShift;
import com.foodie.delivery.repository.DeliveryShiftRepository;
import com.foodie.delivery.service.DeliveryShiftService;
import com.foodie.delivery.service.impl.DeliveryShiftServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/admin/shifts", "/api/v1/admin/delivery/shifts", "/api/v1/admin/delivery-partners/shifts"})
@Tag(name = "Admin — Shift Management")
public class AdminShiftController {

    private final DeliveryShiftService shiftService;

    @Autowired(required = false)
    private AdminAccess adminAccess;

    @Autowired(required = false)
    private DeliveryShiftRepository shiftRepository;

    @Autowired(required = false)
    private LocationZoneRepository locationZoneRepository;

    public AdminShiftController(DeliveryShiftService shiftService) {
        this.shiftService = shiftService;
    }

    public AdminShiftController(DeliveryShiftService shiftService, AdminAccess adminAccess) {
        this.shiftService = shiftService;
        this.adminAccess = adminAccess;
    }

    public AdminShiftController(
            DeliveryShiftService shiftService,
            AdminAccess adminAccess,
            DeliveryShiftRepository shiftRepository,
            LocationZoneRepository locationZoneRepository
    ) {
        this.shiftService = shiftService;
        this.adminAccess = adminAccess;
        this.shiftRepository = shiftRepository;
        this.locationZoneRepository = locationZoneRepository;
    }

    @PostMapping
    @Operation(summary = "Create a new delivery shift")
    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> createShift(
            Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @Valid @RequestBody CreateShiftRequestDto request
    ) {
        AdminUser admin = resolveAdmin(authentication);

        String effectiveZoneId;
        if (admin != null && admin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            // Zone admin: automatically assign each shift to the logged-in admin's zone
            effectiveZoneId = admin.getAssignedZoneId().trim();
        } else {
            String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                    ? activeZoneHeader.trim()
                    : (request.zoneId() != null && !request.zoneId().isBlank() ? request.zoneId().trim() : null);

            effectiveZoneId = adminAccess != null
                    ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                    : requestedZoneId;

            if (effectiveZoneId != null && effectiveZoneId.equalsIgnoreCase("ALL")) {
                effectiveZoneId = null;
            }
        }

        String effectiveZoneName = resolveZoneName(effectiveZoneId, request.zoneName());

        CreateShiftRequestDto scopedRequest = new CreateShiftRequestDto(
                request.shiftName(),
                request.startTime(),
                request.endTime(),
                effectiveZoneId != null ? effectiveZoneId : request.zoneId(),
                effectiveZoneName
        );

        DeliveryShiftResponseDto created = shiftService.createShift(scopedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    @Operation(summary = "List all shifts for admin panel")
    public ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> getAllShifts(
            Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam
    ) {
        AdminUser admin = resolveAdmin(authentication);

        String activeZoneId;
        if (admin != null && admin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            // Zone admin: strictly restricted to their assigned zone
            activeZoneId = admin.getAssignedZoneId().trim();
        } else {
            String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                    ? activeZoneHeader.trim()
                    : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

            activeZoneId = adminAccess != null
                    ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                    : requestedZoneId;
        }

        List<DeliveryShiftResponseDto> list = (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL"))
                ? shiftService.getAllAdminShifts(activeZoneId)
                : shiftService.getAllAdminShifts();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing delivery shift")
    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> updateShift(
            Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftRequestDto request
    ) {
        AdminUser admin = resolveAdmin(authentication);

        if (admin != null && admin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            if (shiftRepository != null) {
                DeliveryShift existingShift = shiftRepository.findById(id).orElseThrow(
                        () -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Shift not found with id: " + id));
                if (existingShift.getZoneId() != null && !DeliveryShiftServiceImpl.isZoneMatch(existingShift.getZoneId(), admin.getAssignedZoneId())) {
                    throw new ForbiddenException("Unauthorized cross-zone update: You can only edit shifts within your assigned zone '" + admin.getAssignedZoneId() + "'.");
                }
            }
        }

        String effectiveZoneId;
        if (admin != null && admin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            effectiveZoneId = admin.getAssignedZoneId().trim();
        } else {
            String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                    ? activeZoneHeader.trim()
                    : (request.zoneId() != null && !request.zoneId().isBlank() ? request.zoneId().trim() : null);

            effectiveZoneId = adminAccess != null
                    ? adminAccess.resolveAuthorizedZone(authentication, requestedZoneId)
                    : requestedZoneId;

            if (effectiveZoneId != null && effectiveZoneId.equalsIgnoreCase("ALL")) {
                effectiveZoneId = null;
            }
        }

        String effectiveZoneName = resolveZoneName(effectiveZoneId, request.zoneName());

        UpdateShiftRequestDto scopedRequest = new UpdateShiftRequestDto(
                request.shiftName(),
                request.startTime(),
                request.endTime(),
                request.status(),
                effectiveZoneId != null ? effectiveZoneId : request.zoneId(),
                effectiveZoneName
        );

        DeliveryShiftResponseDto updated = shiftService.updateShift(id, scopedRequest);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or deactivate a delivery shift")
    public ResponseEntity<ApiResponse<Void>> deleteShift(
            Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @PathVariable UUID id
    ) {
        AdminUser admin = resolveAdmin(authentication);

        if (admin != null && admin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            if (shiftRepository != null) {
                DeliveryShift existingShift = shiftRepository.findById(id).orElseThrow(
                        () -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Shift not found with id: " + id));
                if (existingShift.getZoneId() != null && !DeliveryShiftServiceImpl.isZoneMatch(existingShift.getZoneId(), admin.getAssignedZoneId())) {
                    throw new ForbiddenException("Unauthorized cross-zone delete: You can only delete shifts within your assigned zone '" + admin.getAssignedZoneId() + "'.");
                }
            }
        }

        shiftService.deleteShift(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private AdminUser resolveAdmin(Authentication authentication) {
        if (adminAccess != null && authentication != null && authentication.isAuthenticated()) {
            try {
                return adminAccess.requireAdmin(authentication);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String resolveZoneName(String zoneId, String requestZoneName) {
        if (zoneId == null || zoneId.isBlank()) {
            return requestZoneName;
        }
        if (locationZoneRepository != null) {
            var zoneOpt = locationZoneRepository.findById(zoneId.trim());
            if (zoneOpt.isPresent() && zoneOpt.get().getZoneName() != null && !zoneOpt.get().getZoneName().isBlank()) {
                return zoneOpt.get().getZoneName();
            }
        }
        return (requestZoneName != null && !requestZoneName.isBlank()) ? requestZoneName : zoneId;
    }

    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> createShift(CreateShiftRequestDto request) {
        return createShift(null, null, request);
    }

    public ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> getAllShifts() {
        return getAllShifts(null, null, null);
    }

    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> updateShift(UUID id, UpdateShiftRequestDto request) {
        return updateShift(null, null, id, request);
    }

    public ResponseEntity<ApiResponse<Void>> deleteShift(UUID id) {
        return deleteShift(null, null, id);
    }
}
