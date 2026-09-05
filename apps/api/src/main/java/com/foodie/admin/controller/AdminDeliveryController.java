package com.foodie.admin.controller;

import com.foodie.admin.service.AdminOperationsService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.response.DeliveryProfileResponseDto;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/delivery-partners")
@Tag(name = "Admin — Delivery")
public class AdminDeliveryController {

    private final AdminOperationsService adminOperationsService;
    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public AdminDeliveryController(AdminOperationsService adminOperationsService,
            DeliveryPartnerRepository deliveryPartnerRepository) {
        this.adminOperationsService = adminOperationsService;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    @PatchMapping("/{id}/kyc-approve")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'OPS', 'SUPER_ADMIN')")
    @Operation(summary = "Approve delivery partner KYC")
    public ResponseEntity<ApiResponse<DeliveryProfileResponseDto>> approveKyc(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") UUID partnerId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminOperationsService.approveDeliveryKyc(principal.userId(), partnerId)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all delivery partners")
    public ResponseEntity<ApiResponse<AdminOperationsService.PageResult<DeliveryPartner>>> listDeliveryPartners() {
        List<DeliveryPartner> dpList = deliveryPartnerRepository.findAll();
        AdminOperationsService.PageResult<DeliveryPartner> page = new AdminOperationsService.PageResult<>(
                dpList,
                new com.foodie.common.dto.PaginationMeta(
                        0, dpList.size(), dpList.size(), 1));
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}
