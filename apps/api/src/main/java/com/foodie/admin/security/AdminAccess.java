package com.foodie.admin.security;

import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.PermissionRepository;
import com.foodie.common.enums.UserType;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.security.principal.AuthPrincipal;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * SpEL-friendly RBAC helper for {@code @PreAuthorize("@adminAccess...")}.
 */
@Component("adminAccess")
public class AdminAccess {

    private final AdminUserRepository adminUserRepository;
    private final PermissionRepository permissionRepository;

    public AdminAccess(AdminUserRepository adminUserRepository, PermissionRepository permissionRepository) {
        this.adminUserRepository = adminUserRepository;
        this.permissionRepository = permissionRepository;
    }

    public boolean hasAnyRole(Authentication authentication, String... roles) {
        AdminUser admin = requireAdmin(authentication);
        AdminRoleName name = admin.getRole().getName();
        if (name == AdminRoleName.SUPER_ADMIN) {
            return true;
        }
        return Arrays.stream(roles).anyMatch(r -> isRoleMatch(name, r));
    }

    private boolean isRoleMatch(AdminRoleName name, String r) {
        if (name.name().equalsIgnoreCase(r)) {
            return true;
        }
        if (("OPS".equalsIgnoreCase(r) || "OPERATIONS_ADMIN".equalsIgnoreCase(r))
                && (name == AdminRoleName.OPS || name == AdminRoleName.OPERATIONS_ADMIN)) {
            return true;
        }
        if (("FINANCE".equalsIgnoreCase(r) || "FINANCE_ADMIN".equalsIgnoreCase(r))
                && (name == AdminRoleName.FINANCE || name == AdminRoleName.FINANCE_ADMIN)) {
            return true;
        }
        if (("SUPPORT".equalsIgnoreCase(r) || "SUPPORT_AGENT".equalsIgnoreCase(r))
                && (name == AdminRoleName.SUPPORT || name == AdminRoleName.SUPPORT_AGENT)) {
            return true;
        }
        return false;
    }

    public boolean can(Authentication authentication, String resource, String action) {
        AdminUser admin = requireAdmin(authentication);
        if (admin.getRole().getName() == AdminRoleName.SUPER_ADMIN) {
            return true;
        }
        return permissionRepository.existsByRoleIdAndResourceAndAction(
                admin.getRole().getId(), resource, action);
    }

    public static boolean areZonesEquivalent(String zoneA, String zoneB) {
        if (zoneA == null || zoneB == null) return false;
        String a = zoneA.trim().toLowerCase();
        String b = zoneB.trim().toLowerCase();
        if (a.equals(b)) return true;
        if ((a.contains("bangalore") || a.contains("bengaluru") || a.contains("blr")) && (b.contains("bangalore") || b.contains("bengaluru") || b.contains("blr"))) return true;
        if ((a.contains("tumkur") || a.contains("tumakuru") || a.contains("tmk")) && (b.contains("tumkur") || b.contains("tumakuru") || b.contains("tmk"))) return true;
        if ((a.contains("mysore") || a.contains("mysuru") || a.contains("myq")) && (b.contains("mysore") || b.contains("mysuru") || b.contains("myq"))) return true;
        return a.contains(b) || b.contains(a);
    }

    public boolean canAccessZone(Authentication authentication, String zoneId) {
        AdminUser admin = requireAdmin(authentication);
        if (admin.getRole().getName() == AdminRoleName.SUPER_ADMIN) {
            return true;
        }
        if (admin.getAssignedZoneId() == null || admin.getAssignedZoneId().isBlank()) {
            return true;
        }
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        return areZonesEquivalent(admin.getAssignedZoneId(), zoneId.trim());
    }

    public String resolveAuthorizedZone(Authentication authentication, String requestedZoneId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthPrincipal)) {
            return requestedZoneId;
        }
        AdminUser admin = requireAdmin(authentication);
        if (admin.getRole().getName() == AdminRoleName.SUPER_ADMIN) {
            return requestedZoneId;
        }
        if (admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
            if (requestedZoneId != null && !requestedZoneId.isBlank()) {
                if (requestedZoneId.equalsIgnoreCase("ALL") || !areZonesEquivalent(admin.getAssignedZoneId(), requestedZoneId.trim())) {
                    throw new ForbiddenException("Unauthorized cross-zone access attempt: Your account is restricted to zone '" + admin.getAssignedZoneId() + "'.");
                }
                return requestedZoneId.trim();
            }
            return admin.getAssignedZoneId();
        }
        return requestedZoneId;
    }

    public AdminUser requireAdmin(Authentication authentication) {
        AuthPrincipal principal = principal(authentication);
        if (principal.userType() != UserType.ADMIN) {
            throw new ForbiddenException("Admin access required.");
        }
        return adminUserRepository.findByUserCredentialId(principal.userId())
                .or(() -> adminUserRepository.findById(principal.userId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin profile not found for this credential."));
    }

    public UUID adminUserId(Authentication authentication) {
        return requireAdmin(authentication).getId();
    }

    private static AuthPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenException("Admin access required.");
        }
        return principal;
    }
}
