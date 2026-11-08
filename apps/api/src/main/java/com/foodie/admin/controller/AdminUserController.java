package com.foodie.admin.controller;

import com.foodie.admin.dto.request.CreateAdminUserRequestDto;
import com.foodie.admin.dto.response.AdminUserResponseDto;
import com.foodie.admin.dto.response.UserLoginDeviceResponseDto;
import com.foodie.admin.service.AdminService;
import com.foodie.admin.service.UserLoginDeviceService;
import com.foodie.common.dto.ApiResponse;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/admin/users", "/api/admin/users"})
@Tag(name = "Admin — Users")
public class AdminUserController {

    private final AdminService adminService;
    private final UserLoginDeviceService userLoginDeviceService;

    @Autowired
    public AdminUserController(
            AdminService adminService,
            UserLoginDeviceService userLoginDeviceService
    ) {
        this.adminService = adminService;
        this.userLoginDeviceService = userLoginDeviceService;
    }

    public AdminUserController(AdminService adminService) {
        this(adminService, null);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Current admin profile + sub-role")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> me(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.toResponse(adminService.requireAdminProfile(principal.userId()))));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Provision an admin user (SUPER_ADMIN); login via existing OTP on phone")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateAdminUserRequestDto request
    ) {
        var view = adminService.createAdminUser(
                new AdminService.CreateAdminUserCommand(
                        request.fullName(),
                        request.phoneNumber(),
                        request.email(),
                        request.role(),
                        request.password(),
                        request.assignedZoneId()
                ),
                new AdminService.ActorContext(principal.userId())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(adminService.toResponse(view)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "List all provisioned admin users with device counts")
    public ResponseEntity<ApiResponse<List<AdminUserResponseDto>>> listAll() {
        var users = adminService.listAllAdminUsers().stream()
                .map(adminService::toResponse)
                .map(u -> {
                    if (userLoginDeviceService != null) {
                        String idStr = u.adminUserId() != null ? u.adminUserId().toString() : "";
                        String credIdStr = u.userCredentialId() != null ? u.userCredentialId().toString() : "";
                        int total = (int) Math.max(
                                userLoginDeviceService.countTotalDevices(idStr),
                                userLoginDeviceService.countTotalDevices(credIdStr)
                        );
                        int active = (int) Math.max(
                                userLoginDeviceService.countActiveDevices(idStr),
                                userLoginDeviceService.countActiveDevices(credIdStr)
                        );
                        return new AdminUserResponseDto(
                                u.adminUserId(),
                                u.userCredentialId(),
                                u.fullName(),
                                u.role(),
                                u.profileImageKey(),
                                u.assignedZoneId(),
                                u.email(),
                                u.phoneNumber(),
                                total,
                                active
                        );
                    }
                    return u;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Permanently delete an admin user and their credentials")
    public ResponseEntity<ApiResponse<Boolean>> deleteUser(@PathVariable UUID userId) {
        adminService.deleteAdminUser(userId);
        if (userLoginDeviceService != null) {
            userLoginDeviceService.logoutAllDevices(userId.toString());
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping({"/{userId}/login-devices", "/{userId}/devices"})
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Get login devices history for a user (Super Admin only)")
    public ResponseEntity<ApiResponse<List<UserLoginDeviceResponseDto>>> getLoginDevices(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        boolean maskIp = false;
        List<UserLoginDeviceResponseDto> devices = userLoginDeviceService != null
                ? userLoginDeviceService.getDevicesForUser(userId, maskIp)
                : List.of();
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    @PostMapping("/{userId}/logout-all-devices")
    @PreAuthorize("hasRole('ADMIN') and @adminAccess.hasAnyRole(authentication, 'SUPER_ADMIN')")
    @Operation(summary = "Log out all devices for a user (Super Admin only)")
    public ResponseEntity<ApiResponse<Boolean>> logoutAllDevices(
            @PathVariable String userId
    ) {
        if (userLoginDeviceService != null) {
            userLoginDeviceService.logoutAllDevices(userId);
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }
}
