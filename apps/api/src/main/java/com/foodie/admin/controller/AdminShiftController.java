package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.request.UpdateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.service.DeliveryShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/admin/shifts", "/api/v1/admin/delivery/shifts", "/api/v1/admin/delivery-partners/shifts"})
@Tag(name = "Admin — Shift Management")
public class AdminShiftController {

    private final DeliveryShiftService shiftService;

    public AdminShiftController(DeliveryShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping
    @Operation(summary = "Create a new delivery shift")
    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> createShift(
            @Valid @RequestBody CreateShiftRequestDto request
    ) {
        DeliveryShiftResponseDto created = shiftService.createShift(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    @Operation(summary = "List all shifts for admin panel")
    public ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> getAllShifts() {
        List<DeliveryShiftResponseDto> list = shiftService.getAllAdminShifts();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing delivery shift")
    public ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> updateShift(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftRequestDto request
    ) {
        DeliveryShiftResponseDto updated = shiftService.updateShift(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or deactivate a delivery shift")
    public ResponseEntity<ApiResponse<Void>> deleteShift(@PathVariable UUID id) {
        shiftService.deleteShift(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
