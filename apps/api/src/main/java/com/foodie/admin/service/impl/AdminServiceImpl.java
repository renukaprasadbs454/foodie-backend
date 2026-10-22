package com.foodie.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.admin.dto.response.AdminUserResponseDto;
import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.AuditLog;
import com.foodie.admin.entity.Role;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.AuditLogRepository;
import com.foodie.admin.repository.PermissionRepository;
import com.foodie.admin.repository.RoleRepository;
import com.foodie.admin.service.AdminService;
import com.foodie.common.exception.ConflictException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.shared.contract.AdminCredentialPort;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AdminCredentialPort adminCredentialPort;
    private final ObjectMapper objectMapper;

    public AdminServiceImpl(
            AdminUserRepository adminUserRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AuditLogRepository auditLogRepository,
            AdminCredentialPort adminCredentialPort,
            ObjectMapper objectMapper
    ) {
        this.adminUserRepository = adminUserRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.adminCredentialPort = adminCredentialPort;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AdminUserView createAdminUser(CreateAdminUserCommand cmd, ActorContext creator) {
        AdminUser creatorAdmin = adminUserRepository.findByUserCredentialId(creator.actorUserCredentialId())
                .orElseThrow(() -> new ForbiddenException("Only provisioned admins may create admin users."));
        if (creatorAdmin.getRole().getName() != AdminRoleName.SUPER_ADMIN
                && !hasPermission(creatorAdmin.getId(), "ADMIN_USER", "CREATE")) {
            throw new ForbiddenException("SUPER_ADMIN required to create admin users.");
        }
        Role role = roleRepository.findByName(cmd.role())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
        UUID credentialId = (cmd.password() != null && !cmd.password().isBlank())
                ? adminCredentialPort.ensureAdminCredentialWithPassword(cmd.phoneNumber(), cmd.email(), cmd.password())
                : adminCredentialPort.ensureAdminCredential(cmd.phoneNumber(), cmd.email());
        AdminUser created;
        if (adminUserRepository.existsByUserCredentialId(credentialId)) {
            created = adminUserRepository.findByUserCredentialId(credentialId).orElseThrow();
            created.setRole(role);
            created.setFullName(cmd.fullName().trim());
            created.setAssignedZoneId(cmd.assignedZoneId());
            created = adminUserRepository.save(created);
        } else {
            created = AdminUser.create(credentialId, role, cmd.fullName().trim(), cmd.assignedZoneId());
            try {
                created = adminUserRepository.save(created);
            } catch (DataIntegrityViolationException ex) {
                created = adminUserRepository.findByUserCredentialId(credentialId)
                        .orElseThrow(() -> new ConflictException(ErrorCode.CONFLICT, "Admin profile already exists for this credential."));
                created.setRole(role);
                created.setFullName(cmd.fullName().trim());
                created.setAssignedZoneId(cmd.assignedZoneId());
                created = adminUserRepository.save(created);
            }
        }
        recordAudit(
                creatorAdmin.getId(),
                "CREATE_ADMIN_USER",
                "ADMIN_USER",
                created.getId(),
                null,
                Map.of(
                        "adminUserId", created.getId(),
                        "role", role.getName().name(),
                        "fullName", created.getFullName(),
                        "assignedZoneId", created.getAssignedZoneId() != null ? created.getAssignedZoneId() : "NONE"
                )
        );
        return toView(created);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID adminUserId, String resource, String action) {
        AdminUser admin = adminUserRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found."));
        if (admin.getRole().getName() == AdminRoleName.SUPER_ADMIN) {
            return true;
        }
        return permissionRepository.existsByRoleIdAndResourceAndAction(
                admin.getRole().getId(), resource, action);
    }

    @Override
    @Transactional
    public void recordAudit(
            UUID adminUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Object before,
            Object after
    ) {
        auditLogRepository.save(AuditLog.append(
                adminUserId,
                action,
                resourceType,
                resourceId,
                toJson(before),
                toJson(after)
        ));
        log.debug("audit action={} resourceType={} resourceId={} admin={}",
                action, resourceType, resourceId, adminUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserView requireAdminProfile(UUID userCredentialId) {
        return adminUserRepository.findByUserCredentialId(userCredentialId)
                .or(() -> adminUserRepository.findById(userCredentialId))
                .map(this::toView)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin profile not found for this credential."));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AdminUserView> listAllAdminUsers() {
        return adminUserRepository.findAll().stream()
                .map(this::toView)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAdminUser(UUID adminUserId) {
        if (adminUserId == null) return;
        AdminUser admin = adminUserRepository.findById(adminUserId)
                .or(() -> adminUserRepository.findByUserCredentialId(adminUserId))
                .orElse(null);
        if (admin != null) {
            UUID credId = admin.getUserCredentialId();
            adminUserRepository.delete(admin);
            if (credId != null) {
                adminCredentialPort.deleteAdminCredential(credId);
            }
        }
    }

    @Override
    @Transactional
    public void deleteAdminUsersByZoneId(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return;
        String clean = zoneId.trim().toLowerCase();
        java.util.List<AdminUser> toDelete = adminUserRepository.findAll().stream()
                .filter(u -> {
                    if (u.getAssignedZoneId() == null) return false;
                    String uZone = u.getAssignedZoneId().trim().toLowerCase();
                    return uZone.equalsIgnoreCase(clean) ||
                            uZone.contains(clean) ||
                            clean.contains(uZone);
                })
                .collect(java.util.stream.Collectors.toList());
        for (AdminUser admin : toDelete) {
            UUID credId = admin.getUserCredentialId();
            adminUserRepository.delete(admin);
            if (credId != null) {
                adminCredentialPort.deleteAdminCredential(credId);
            }
        }
    }

    @Override
    public AdminUserResponseDto toResponse(AdminUserView view) {
        return new AdminUserResponseDto(
                view.adminUserId(),
                view.userCredentialId(),
                view.fullName(),
                view.role(),
                view.profileImageKey(),
                view.assignedZoneId(),
                view.email(),
                view.phoneNumber()
        );
    }

    private AdminUserView toView(AdminUser admin) {
        String email = admin.getUserCredentialId() != null
                ? adminCredentialPort.findEmailByUserCredentialId(admin.getUserCredentialId()).orElse(null)
                : null;
        String phone = admin.getUserCredentialId() != null
                ? adminCredentialPort.findPhoneNumberByUserCredentialId(admin.getUserCredentialId()).orElse(null)
                : null;
        return new AdminUserView(
                admin.getId(),
                admin.getUserCredentialId(),
                admin.getFullName(),
                admin.getRole().getName(),
                admin.getProfileImageKey(),
                admin.getAssignedZoneId(),
                email,
                phone
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String s) {
                return s;
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"_error\":\"unserializable\"}";
        }
    }
}
