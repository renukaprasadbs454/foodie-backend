package com.foodie.admin.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.response.CashDepositResponseDto;
import com.foodie.delivery.dto.response.DeliveryProfileResponseDto;
import com.foodie.delivery.dto.response.LivePartnerLocationDto;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.service.DeliveryService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/delivery")
@Tag(name = "Admin — Delivery & Fleet")
public class AdminDeliveryController {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryService deliveryService;

    public AdminDeliveryController(
            DeliveryPartnerRepository deliveryPartnerRepository,
            DeliveryService deliveryService) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{id}/approve-kyc")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve delivery partner KYC")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> approveKyc(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID partnerId) {
        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.verifyKyc(partnerId, principal.userId())));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all delivery partners")
    public ResponseEntity<ApiResponse<List<DeliveryPartner>>> listDeliveryPartners() {
        return ResponseEntity.ok(ApiResponse.success(deliveryPartnerRepository.findAll()));
    }

    @GetMapping("/live-locations")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get live GPS locations and status of all online delivery partners")
    public ResponseEntity<ApiResponse<List<LivePartnerLocationDto>>> getLiveFleetLocations() {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.getLiveFleetLocations()));
    }

    @GetMapping("/cash-deposits")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all PENDING cash deposit requests submitted by delivery partners")
    public ResponseEntity<ApiResponse<List<CashDepositResponseDto>>> listPendingCashDeposits() {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.listPendingCashDeposits()));
    }

    @PostMapping("/cash-deposits/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a delivery partner cash deposit request and clear cash balance")
    public ResponseEntity<ApiResponse<CashDepositResponseDto>> approveCashDeposit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.approveCashDeposit(id, principal.userId())));
    }

    @PostMapping("/cash-deposits/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a delivery partner cash deposit request")
    public ResponseEntity<ApiResponse<CashDepositResponseDto>> rejectCashDeposit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Deposit verification failed.") String reason) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.rejectCashDeposit(id, principal.userId(), reason)));
    }
}
