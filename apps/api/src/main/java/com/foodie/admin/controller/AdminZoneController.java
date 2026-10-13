package com.foodie.admin.controller;

import com.foodie.admin.dto.ActiveOperatingZoneDto;
import com.foodie.admin.dto.AdminZoneLoginHistoryDto;
import com.foodie.admin.dto.ZoneSelectionRequestDto;
import com.foodie.admin.dto.ZoneSelectionResponseDto;
import com.foodie.admin.service.AdminZoneService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.common.dto.PaginationMeta;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/zones")
@Tag(name = "Admin — Zone Access & Login History")
public class AdminZoneController {

    private final AdminZoneService adminZoneService;

    public AdminZoneController(AdminZoneService adminZoneService) {
        this.adminZoneService = adminZoneService;
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get list of active operating zones for Super Admin selection")
    public ResponseEntity<ApiResponse<List<ActiveOperatingZoneDto>>> getAvailableZones(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        List<ActiveOperatingZoneDto> zones = adminZoneService.getAvailableZones(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(zones));
    }

    @PostMapping("/select")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Super Admin selects active operating zone and records login activity")
    public ResponseEntity<ApiResponse<ZoneSelectionResponseDto>> selectZone(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ZoneSelectionRequestDto request,
            HttpServletRequest servletRequest
    ) {
        String ip = servletRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = servletRequest.getRemoteAddr();
        }
        String userAgent = servletRequest.getHeader("User-Agent");

        ZoneSelectionResponseDto response = adminZoneService.selectZone(
                principal.userId(),
                request.zoneId(),
                ip,
                userAgent
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Get paginated Super Admin zone login & access history")
    public ResponseEntity<ApiResponse<List<AdminZoneLoginHistoryDto>>> getLoginHistory(
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminZoneLoginHistoryDto> historyPage = adminZoneService.getLoginHistory(zoneId, search, page, size);
        PaginationMeta meta = new PaginationMeta(
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(historyPage.getContent(), meta));
    }

    @GetMapping("/validate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Validate operating zone access")
    public ResponseEntity<ApiResponse<String>> validateZone(
            @RequestParam String zoneId
    ) {
        var zone = adminZoneService.validateZone(zoneId);
        return ResponseEntity.ok(ApiResponse.success("Zone valid and active: " + zone.getZoneName()));
    }
}
