package com.foodie.admin.config;

import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.Role;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.RoleRepository;
import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.enums.UserType;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevAdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminUserSeeder.class);

    private final UserCredentialRepository userCredentialRepository;
    private final AdminUserRepository adminUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevAdminUserSeeder(
            UserCredentialRepository userCredentialRepository,
            AdminUserRepository adminUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userCredentialRepository = userCredentialRepository;
        this.adminUserRepository = adminUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record AdminSeedDef(String email, String password, String fullName, AdminRoleName roleName, String phone) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        // 1. Ensure Roles exist
        for (AdminRoleName roleName : AdminRoleName.values()) {
            roleRepository.findByName(roleName).orElseGet(() -> {
                UUID roleId = switch (roleName) {
                    case SUPER_ADMIN -> UUID.fromString("11111111-1111-1111-1111-111111111001");
                    case OPS -> UUID.fromString("11111111-1111-1111-1111-111111111002");
                    case FINANCE -> UUID.fromString("11111111-1111-1111-1111-111111111003");
                    case SUPPORT -> UUID.fromString("11111111-1111-1111-1111-111111111004");
                };
                Role r = roleRepository.save(Role.ref(roleId, roleName));
                log.info("Created missing role {} ({})", roleName, roleId);
                return r;
            });
        }

        // 2. Ensure Admin users exist with correct passwords
        List<AdminSeedDef> seeds = List.of(
                new AdminSeedDef("admin@foodie.local", "ChangeMe@123", "Super Admin", AdminRoleName.SUPER_ADMIN, "+919999999991"),
                new AdminSeedDef("superadmin@foodie.local", "ChangeMe@123", "Bootstrap Super Admin", AdminRoleName.SUPER_ADMIN, "+919999999999"),
                new AdminSeedDef("financeadmin@foodie.local", "FoodieMinister@111", "Finance Admin", AdminRoleName.FINANCE, "+919999999992"),
                new AdminSeedDef("opsadmin@foodie.local", "FoodieOps@222", "Operations Admin", AdminRoleName.OPS, "+919999999993"),
                new AdminSeedDef("manager@foodie.local", "FoodieManager@333", "Restaurant Manager", AdminRoleName.OPS, "+919999999994"),
                new AdminSeedDef("support@foodie.local", "FoodieSupport@444", "Support Agent", AdminRoleName.SUPPORT, "+919999999995"),
                new AdminSeedDef("auditor@foodie.local", "FoodieAuditor@555", "Compliance Auditor", AdminRoleName.SUPER_ADMIN, "+919999999996"),
                new AdminSeedDef("darkstore@foodie.local", "DarkstoreOps@123", "Darkstore Admin", AdminRoleName.OPS, "+919999999997")
        );

        for (AdminSeedDef seedDef : seeds) {
            try {
                Role role = roleRepository.findByName(seedDef.roleName()).orElseThrow();

                UserCredential cred = userCredentialRepository.findByEmailIgnoreCaseAndUserType(seedDef.email(), UserType.ADMIN)
                        .orElseGet(() -> {
                            UserCredential newCred = UserCredential.adminProvisionWithPassword(
                                    seedDef.phone(),
                                    seedDef.email().toLowerCase(),
                                    passwordEncoder.encode(seedDef.password())
                            );
                            return userCredentialRepository.save(newCred);
                        });

                cred.updatePasswordHash(passwordEncoder.encode(seedDef.password()));
                userCredentialRepository.save(cred);

                if (!adminUserRepository.existsByUserCredentialId(cred.getId())) {
                    AdminUser adminUser = AdminUser.create(cred.getId(), role, seedDef.fullName());
                    adminUserRepository.save(adminUser);
                    log.info("Seeded admin user {} with role {}", seedDef.email(), seedDef.roleName());
                } else {
                    log.info("Admin user {} already exists with valid credentials", seedDef.email());
                }
            } catch (Exception ex) {
                log.warn("Could not seed admin user {}: {}", seedDef.email(), ex.getMessage());
            }
        }
    }
}
