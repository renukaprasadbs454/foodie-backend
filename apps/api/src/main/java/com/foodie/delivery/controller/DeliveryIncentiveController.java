package com.foodie.delivery.controller;

import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.response.IncentiveEarningHistoryDto;
import com.foodie.delivery.dto.response.IncentiveOfferProgressDto;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
import com.foodie.delivery.service.DeliveryIncentiveService;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery/incentives")
@Tag(name = "Delivery — Incentives")
public class DeliveryIncentiveController {

    private final DeliveryIncentiveService incentiveService;

    public DeliveryIncentiveController(DeliveryIncentiveService incentiveService) {
        this.incentiveService = incentiveService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Get active delivery partner incentive offers")
    public ResponseEntity<ApiResponse<List<IncentiveOfferProgressDto>>> getActiveIncentives(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                incentiveService.getActiveIncentives(principal.userId())
        ));
    }

    @GetMapping("/progress")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Get daily incentives progress and offers for a given date")
    public ResponseEntity<ApiResponse<IncentivesProgressResponseDto>> getIncentivesProgress(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                incentiveService.getIncentivesProgress(principal.userId(), date)
        ));
    }

    @GetMapping("/earnings")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @Operation(summary = "Get delivery partner incentive earning history")
    public ResponseEntity<ApiResponse<List<IncentiveEarningHistoryDto>>> getIncentiveEarnings(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                incentiveService.getIncentiveEarnings(principal.userId())
        ));
    }
}
