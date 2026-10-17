package com.foodie.delivery.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.response.DeliveryShiftBookingResponseDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.service.DeliveryShiftService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery/shifts")
@Tag(name = "Delivery — Shift Booking & Availability")
public class DeliveryShiftController {

    private static final UUID DEFAULT_DEV_PARTNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final DeliveryShiftService shiftService;

    public DeliveryShiftController(DeliveryShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping("/available")
    @Operation(summary = "List all active shifts available for delivery partners")
    public ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> getAvailableShifts() {
        List<DeliveryShiftResponseDto> available = shiftService.getAvailableShifts();
        return ResponseEntity.ok(ApiResponse.success(available));
    }

    @PostMapping("/{id}/book")
    @Operation(summary = "Book an available shift as a delivery partner")
    public ResponseEntity<ApiResponse<DeliveryShiftBookingResponseDto>> bookShift(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Delivery-Partner-Id", required = false) String customPartnerIdHeader,
            @PathVariable UUID id
    ) {
        UUID partnerId = resolvePartnerId(principal, customPartnerIdHeader);
        DeliveryShiftBookingResponseDto booking = shiftService.bookShift(partnerId, id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(booking));
    }

    @GetMapping("/my-bookings")
    @Operation(summary = "List current delivery partner's booked shifts")
    public ResponseEntity<ApiResponse<List<DeliveryShiftBookingResponseDto>>> getMyBookings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "X-Delivery-Partner-Id", required = false) String customPartnerIdHeader
    ) {
        UUID partnerId = resolvePartnerId(principal, customPartnerIdHeader);
        List<DeliveryShiftBookingResponseDto> bookings = shiftService.getMyBookings(partnerId);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    private UUID resolvePartnerId(AuthPrincipal principal, String header) {
        if (principal != null && principal.userId() != null) {
            return principal.userId();
        }
        if (header != null && !header.isBlank()) {
            try {
                return UUID.fromString(header.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        return DEFAULT_DEV_PARTNER_ID;
    }
}
