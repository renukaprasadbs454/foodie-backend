package com.foodie.delivery.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.common.enums.DeliveryDocType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.delivery.dto.request.LocationPingRequestDto;
import com.foodie.delivery.dto.request.SetAvailabilityRequestDto;
import com.foodie.delivery.dto.request.UpsertDeliveryProfileRequestDto;
import com.foodie.delivery.dto.request.VerifyOtpRequestDto;
import com.foodie.delivery.dto.response.AvailabilityResponseDto;
import com.foodie.delivery.dto.response.DeliveryAssignmentResponseDto;
import com.foodie.delivery.dto.response.DeliveryDocumentResponseDto;
import com.foodie.delivery.dto.response.DeliveryOfferResponseDto;
import com.foodie.delivery.dto.response.DeliveryProfileResponseDto;
import com.foodie.delivery.service.DeliveryService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/delivery")
@Tag(name = "Delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Get my delivery partner profile")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> getProfile(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.getOrCreateProfile(principal.userId())));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Create or update my delivery partner profile")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> upsertProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpsertDeliveryProfileRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.upsertProfile(principal.userId(), request)));
    }

    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Upload delivery partner KYC document (never self-verifies)")
    public ResponseEntity<ApiResponse<DeliveryDocumentResponseDto>> uploadDocument(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestPart("docType") String docType,
            @RequestPart("file") MultipartFile file
    ) {
        DeliveryDocType type;
        try {
            type = DeliveryDocType.valueOf(docType);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED,
                    "docType must be LICENSE, VEHICLE_RC, or IDENTITY."
            );
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(deliveryService.uploadDocument(principal.userId(), type, file)));
    }

    @PostMapping("/availability")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Set online/offline availability")
    public ResponseEntity<ApiResponse<AvailabilityResponseDto>> setAvailability(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SetAvailabilityRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.setAvailability(principal.userId(), request)));
    }

    @GetMapping("/offers")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "List OFFERED delivery assignments for this partner")
    public ResponseEntity<ApiResponse<List<DeliveryOfferResponseDto>>> listOffers(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.listOffers(principal.userId())));
    }

    @PostMapping("/assignments/{id}/accept")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Accept a delivery assignment offer")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponseDto>> accept(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.accept(principal.userId(), id)));
    }

    @PostMapping("/assignments/{id}/verify-pickup")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Verify restaurant pickup OTP")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponseDto>> verifyPickup(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VerifyOtpRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.verifyPickup(principal.userId(), id, request)));
    }

    @PostMapping("/assignments/{id}/verify-delivery")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Verify customer delivery OTP")
    public ResponseEntity<ApiResponse<DeliveryAssignmentResponseDto>> verifyDelivery(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VerifyOtpRequestDto request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.verifyDelivery(principal.userId(), id, request)));
    }

    @PostMapping("/location-ping")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Report live GPS location (Redis GEO only)")
    public ResponseEntity<Void> locationPing(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody LocationPingRequestDto request
    ) {
        deliveryService.locationPing(principal.userId(), request);
        return ResponseEntity.noContent().build();
    }
}
