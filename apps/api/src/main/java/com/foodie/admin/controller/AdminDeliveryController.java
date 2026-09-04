package com.foodie.admin.controller;

import com.foodie.admin.dto.request.RejectKycRequestDto;
import com.foodie.admin.service.AdminOperationsService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.response.AdminDeliveryPartnerResponseDto;
import com.foodie.delivery.dto.response.DeliveryProfileResponseDto;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/delivery-partners")
@Tag(name = "Admin — Delivery")
public class AdminDeliveryController {

    private final AdminOperationsService adminOperationsService;

    public AdminDeliveryController(AdminOperationsService adminOperationsService) {
        this.adminOperationsService = adminOperationsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "List delivery partners for admin")
    public ResponseEntity<ApiResponse<AdminOperationsService.PageResult<AdminDeliveryPartnerResponseDto>>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort
    ) {
        UUID actorCredentialId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.listDeliveryPartners(actorCredentialId, status, search, page, size, sort)));
    }

    @PatchMapping("/{id}/kyc-approve")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Approve delivery partner KYC")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> approveKyc(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID partnerId
    ) {
        UUID actorCredentialId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.approveDeliveryKyc(actorCredentialId, partnerId)));
    }

    @PatchMapping("/{id}/kyc-reject")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Reject delivery partner KYC")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> rejectKyc(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID partnerId,
            @Valid @RequestBody(required = false) RejectKycRequestDto request
    ) {
        UUID actorCredentialId = principal != null ? principal.userId() : null;
        String reason = (request != null && request.reason() != null) ? request.reason() : "Documents incomplete or invalid";
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.rejectDeliveryKyc(actorCredentialId, partnerId, reason)));
    }
}
