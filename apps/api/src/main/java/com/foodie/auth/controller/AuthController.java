package com.foodie.auth.controller;

import com.foodie.auth.dto.request.AdminLoginRequestDto;
import com.foodie.auth.dto.request.CustomerLoginRequestDto;
import com.foodie.auth.dto.request.CustomerRegisterRequestDto;
import com.foodie.auth.dto.request.ForgotPasswordRequestDto;
import com.foodie.auth.dto.request.GoogleAuthRequestDto;
import com.foodie.auth.dto.request.LogoutRequestDto;
import com.foodie.auth.dto.request.RefreshTokenRequestDto;
import com.foodie.auth.dto.request.RequestOtpRequestDto;
import com.foodie.auth.dto.request.ResetPasswordRequestDto;
import com.foodie.auth.dto.request.VerifyOtpRequestDto;
import com.foodie.auth.dto.response.TokenPairResponseDto;
import com.foodie.auth.service.AuthService;
import com.foodie.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.auth.dto.response.AdminRoleOptionDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final LocationZoneRepository locationZoneRepository;
    private final AdminUserRepository adminUserRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
            AuthService authService,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            LocationZoneRepository locationZoneRepository,
            AdminUserRepository adminUserRepository
    ) {
        this.authService = authService;
        this.jdbcTemplate = jdbcTemplate;
        this.locationZoneRepository = locationZoneRepository;
        this.adminUserRepository = adminUserRepository;
    }

    public AuthController(AuthService authService, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this(authService, jdbcTemplate, null, null);
    }

    private String formatZoneAdminLabel(String zoneName) {
        if (zoneName == null || zoneName.isBlank()) {
            return "Zone Admin";
        }
        String clean = zoneName.trim();
        String lower = clean.toLowerCase();
        if (lower.endsWith("zone admin") || lower.endsWith("admin")) {
            return clean;
        }
        if (lower.endsWith("zone")) {
            return clean + " Admin";
        }
        return clean + " Zone Admin";
    }

    @GetMapping({"/admin-roles", "/roles"})
    @Operation(summary = "Get dynamic admin roles and zone admin accounts for admin login role dropdown")
    public ResponseEntity<ApiResponse<List<AdminRoleOptionDto>>> getAdminRoles() {
        List<AdminRoleOptionDto> options = new ArrayList<>();

        // 1. Super Admin (Default Global Admin)
        options.add(new AdminRoleOptionDto(
                "SUPER_ADMIN",
                "SUPER_ADMIN",
                "Super Admin",
                null,
                null,
                "admin@foodie.local"
        ));

        // 2. Dynamic Zone Admins from database & provisioned admin accounts
        List<LocationZone> zones = (locationZoneRepository != null)
                ? locationZoneRepository.findAll()
                : List.of();

        List<AdminUser> adminUsers = (adminUserRepository != null)
                ? adminUserRepository.findAll()
                : List.of();

        Map<String, AdminUser> zoneAdminMap = new HashMap<>();
        for (AdminUser u : adminUsers) {
            if (u.getAssignedZoneId() != null && !u.getAssignedZoneId().isBlank()) {
                zoneAdminMap.putIfAbsent(u.getAssignedZoneId().trim().toLowerCase(), u);
            }
        }

        // Map canonical zones + any active created zones (do NOT resurrect deleted zones)
        List<LocationZone> activeZones = new ArrayList<>();
        for (LocationZone z : zones) {
            if (z.getId() == null) continue;
            if ("ACTIVE".equalsIgnoreCase(z.getStatus())) {
                activeZones.add(z);
            }
        }

        // Sort zones: Bangalore first, then Tumkur, then newly created alphabetically
        activeZones.sort((a, b) -> {
            if ("dz-bangalore".equalsIgnoreCase(a.getId())) return -1;
            if ("dz-bangalore".equalsIgnoreCase(b.getId())) return 1;
            if ("dz-tumkur".equalsIgnoreCase(a.getId())) return -1;
            if ("dz-tumkur".equalsIgnoreCase(b.getId())) return 1;
            String nameA = a.getZoneName() != null ? a.getZoneName() : a.getId();
            String nameB = b.getZoneName() != null ? b.getZoneName() : b.getId();
            return nameA.compareToIgnoreCase(nameB);
        });

        for (LocationZone z : activeZones) {
            String zId = z.getId();
            String zName = z.getZoneName() != null ? z.getZoneName() : zId;
            String checkStr = ((zId != null ? zId : "") + " " + (zName != null ? zName : "")).toLowerCase();
            if (checkStr.contains("customer")) {
                continue;
            }
            AdminUser assignedUser = zoneAdminMap.get(zId.toLowerCase());
            String role = (assignedUser != null && assignedUser.getRole() != null)
                    ? assignedUser.getRole().getName().name()
                    : "OPS";
            String email = null;
            if (checkStr.contains("bangalore") || checkStr.contains("bengaluru")) {
                email = "ops@foodie.local";
            } else if (checkStr.contains("tumkur") || checkStr.contains("tumakuru")) {
                email = "finance@foodie.local";
            }

            options.add(new AdminRoleOptionDto(
                    "ZONE_ADMIN_" + zId,
                    role,
                    formatZoneAdminLabel(zName),
                    zId,
                    zName,
                    email
            ));
        }

        // 3. Existing system roles
        options.add(new AdminRoleOptionDto("AUDITOR", "AUDITOR", "Compliance Auditor", null, null, "auditor@foodie.local"));
        options.add(new AdminRoleOptionDto("FINANCE_ADMIN", "FINANCE_ADMIN", "Finance Admin", null, null, "finance@foodie.local"));
        options.add(new AdminRoleOptionDto("OPERATIONS_ADMIN", "OPERATIONS_ADMIN", "Operations Admin", null, null, "ops@foodie.local"));
        options.add(new AdminRoleOptionDto("RESTAURANT_MANAGER", "RESTAURANT_MANAGER", "Restaurant Manager", null, null, "manager@foodie.local"));
        options.add(new AdminRoleOptionDto("SUPPORT_AGENT", "SUPPORT_AGENT", "Support Agent", null, null, "support@foodie.local"));
        options.add(new AdminRoleOptionDto("DARKSTORE_ADMIN", "DARKSTORE_ADMIN", "Darkstore Admin", null, null, "darkstore@foodie.local"));

        return ResponseEntity.ok(ApiResponse.success(options));
    }

    @org.springframework.web.bind.annotation.GetMapping("/dev/approve")
    public String approveSpecific() {
        try {
            int rows1 = jdbcTemplate.update(
                    "UPDATE restaurant SET status = 'APPROVED' WHERE owner_user_credential_id IN " +
                            "(SELECT id FROM user_credential WHERE phone_number LIKE '%9972301895%')");
            int rows2 = jdbcTemplate.update(
                    "UPDATE restaurant SET status = 'APPROVED' WHERE status = 'PENDING'");
            return "SUCCESS: Restaurant approved. Rows updated: " + (rows1 + rows2);
        } catch (Exception e) {
            e.printStackTrace();
            return "ERR: " + e.getMessage();
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Customer Registration with email/password")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> register(
            @Valid @RequestBody CustomerRegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.registerCustomer(request)));
    }

    @PostMapping("/login/customer")
    @Operation(summary = "Customer Email/Password login")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> loginCustomer(
            @Valid @RequestBody CustomerLoginRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginCustomer(request)));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset code via email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDto request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset customer password using reset code")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDto request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request OTP", description = "Generate and dispatch a one-time password via SMS.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP dispatched"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid phone"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limited")
    })
    public ResponseEntity<ApiResponse<Void>> requestOtp(@Valid @RequestBody RequestOtpRequestDto request) {
        authService.requestOtp(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and authenticate")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyOtp(request)));
    }

    @PostMapping("/google")
    @Operation(summary = "Authenticate Customer via Google ID token")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> google(
            @Valid @RequestBody GoogleAuthRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(authService.authenticateWithGoogle(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Admin email/password login", description = "Authenticates ADMIN credentials. Reuses the platform JWT + refresh-token pair. "
            + "Customer/Restaurant/Delivery must not call this endpoint.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Account deactivated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limited")
    })
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> login(
            @Valid @RequestBody AdminLoginRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(authService.loginAdmin(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token")
    public ResponseEntity<ApiResponse<TokenPairResponseDto>> refresh(
            @Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token (single-device logout)")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.revoke(request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }
}
