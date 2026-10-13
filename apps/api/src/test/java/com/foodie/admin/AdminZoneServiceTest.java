package com.foodie.admin;

import com.foodie.admin.dto.ActiveOperatingZoneDto;
import com.foodie.admin.dto.AdminZoneLoginHistoryDto;
import com.foodie.admin.dto.ZoneSelectionResponseDto;
import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.AdminZoneLoginHistory;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.entity.Role;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.AdminZoneLoginHistoryRepository;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.service.AdminService;
import com.foodie.admin.service.impl.AdminZoneServiceImpl;
import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminZoneServiceTest {

    @Mock private LocationZoneRepository locationZoneRepository;
    @Mock private AdminZoneLoginHistoryRepository loginHistoryRepository;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private AdminService adminService;

    private AdminZoneServiceImpl adminZoneService;

    private LocationZone bangaloreZone;
    private LocationZone tumkurZone;
    private LocationZone inactiveZone;
    private AdminUser superAdminUser;
    private UserCredential adminCredential;

    @BeforeEach
    void setUp() {
        adminZoneService = new AdminZoneServiceImpl(
                locationZoneRepository,
                loginHistoryRepository,
                adminUserRepository,
                userCredentialRepository,
                adminService
        );

        bangaloreZone = new LocationZone();
        bangaloreZone.setId("dz-bangalore");
        bangaloreZone.setZoneName("Bangalore Zone");
        bangaloreZone.setCityName("Bangalore");
        bangaloreZone.setStatus("ACTIVE");
        bangaloreZone.setLatitude(12.9716);
        bangaloreZone.setLongitude(77.5946);
        bangaloreZone.setRadiusKm(25.0);
        bangaloreZone.setRestaurantEnabled(true);
        bangaloreZone.setDeliveryPartnerEnabled(true);
        bangaloreZone.setCustomerOrderingEnabled(true);
        bangaloreZone.setActiveDrivers(45);

        tumkurZone = new LocationZone();
        tumkurZone.setId("dz-tumkur");
        tumkurZone.setZoneName("Tumkur Zone");
        tumkurZone.setCityName("Tumkur");
        tumkurZone.setStatus("ACTIVE");
        tumkurZone.setLatitude(13.3379);
        tumkurZone.setLongitude(77.1173);
        tumkurZone.setRadiusKm(20.0);
        tumkurZone.setRestaurantEnabled(true);
        tumkurZone.setDeliveryPartnerEnabled(true);
        tumkurZone.setCustomerOrderingEnabled(true);
        tumkurZone.setActiveDrivers(22);

        inactiveZone = new LocationZone();
        inactiveZone.setId("dz-inactive");
        inactiveZone.setZoneName("Inactive Zone");
        inactiveZone.setStatus("INACTIVE");

        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.SUPER_ADMIN);
        UUID credId = UUID.fromString("33333333-3333-3333-3333-333333333001");
        superAdminUser = AdminUser.create(credId, role, "Master Super Admin");

        adminCredential = UserCredential.adminProvision("+919999999991", "admin@foodie.local");
    }

    @Test
    void getAvailableZones_returnsActiveZonesSortedByName() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(tumkurZone, bangaloreZone, inactiveZone));

        List<ActiveOperatingZoneDto> zones = adminZoneService.getAvailableZones(superAdminUser.getUserCredentialId());

        assertThat(zones).hasSize(2);
        assertThat(zones.get(0).zoneName()).isEqualTo("Bangalore Zone");
        assertThat(zones.get(1).zoneName()).isEqualTo("Tumkur Zone");
        assertThat(zones).extracting(ActiveOperatingZoneDto::status).containsOnly("ACTIVE");
    }

    @Test
    void selectZone_validActiveZone_persistsLoginHistoryAndReturnsContext() {
        UUID credId = superAdminUser.getUserCredentialId();
        when(locationZoneRepository.findById("dz-bangalore")).thenReturn(Optional.of(bangaloreZone));
        when(adminUserRepository.findByUserCredentialId(credId)).thenReturn(Optional.of(superAdminUser));
        when(userCredentialRepository.findById(credId)).thenReturn(Optional.of(adminCredential));

        when(loginHistoryRepository.save(any(AdminZoneLoginHistory.class))).thenAnswer(inv -> {
            AdminZoneLoginHistory h = inv.getArgument(0);
            return h;
        });

        ZoneSelectionResponseDto response = adminZoneService.selectZone(
                credId,
                "dz-bangalore",
                "192.168.1.100",
                "Mozilla/5.0"
        );

        assertThat(response).isNotNull();
        assertThat(response.zoneId()).isEqualTo("dz-bangalore");
        assertThat(response.zoneName()).isEqualTo("Bangalore Zone");
        assertThat(response.adminName()).isEqualTo("Master Super Admin");
        assertThat(response.adminEmail()).isEqualTo("admin@foodie.local");
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(response.sessionStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<AdminZoneLoginHistory> captor = ArgumentCaptor.forClass(AdminZoneLoginHistory.class);
        verify(loginHistoryRepository).save(captor.capture());
        AdminZoneLoginHistory saved = captor.getValue();
        assertThat(saved.getAdminName()).isEqualTo("Master Super Admin");
        assertThat(saved.getAdminEmail()).isEqualTo("admin@foodie.local");
        assertThat(saved.getZoneId()).isEqualTo("dz-bangalore");
        assertThat(saved.getZoneName()).isEqualTo("Bangalore Zone");
        assertThat(saved.getSessionStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.1.100");
    }

    @Test
    void selectZone_throwsForInactiveZone() {
        when(locationZoneRepository.findById("dz-inactive")).thenReturn(Optional.of(inactiveZone));

        assertThatThrownBy(() -> adminZoneService.selectZone(
                superAdminUser.getUserCredentialId(),
                "dz-inactive",
                "127.0.0.1",
                "curl"
        )).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INACTIVE");
    }

    @Test
    void selectZone_throwsForUnknownZone() {
        when(locationZoneRepository.findById("dz-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminZoneService.selectZone(
                superAdminUser.getUserCredentialId(),
                "dz-unknown",
                "127.0.0.1",
                "curl"
        )).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("dz-unknown");
    }

    @Test
    void getLoginHistory_returnsPaginatedRecords() {
        AdminZoneLoginHistory h1 = new AdminZoneLoginHistory(
                UUID.randomUUID(),
                superAdminUser.getId(),
                "Master Super Admin",
                "admin@foodie.local",
                "SUPER_ADMIN",
                "dz-bangalore",
                "Bangalore Zone",
                Instant.now(),
                "ACTIVE",
                "127.0.0.1",
                "Chrome"
        );

        when(loginHistoryRepository.searchHistory(eq("dz-bangalore"), eq("admin"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h1)));

        Page<AdminZoneLoginHistoryDto> page = adminZoneService.getLoginHistory("dz-bangalore", "admin", 0, 10);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).adminEmail()).isEqualTo("admin@foodie.local");
        assertThat(page.getContent().get(0).zoneName()).isEqualTo("Bangalore Zone");
    }

    @Test
    void isLocationInZone_boundaryAndCityMatching() {
        // City match
        assertThat(adminZoneService.isLocationInZone(null, null, "Bangalore", bangaloreZone)).isTrue();
        assertThat(adminZoneService.isLocationInZone(null, null, "Tumkur", bangaloreZone)).isFalse();

        // Coordinates within 25km radius of Bangalore center (12.9716, 77.5946)
        // Indiranagar is ~5km away (12.9784, 77.6408)
        assertThat(adminZoneService.isLocationInZone(12.9784, 77.6408, null, bangaloreZone)).isTrue();

        // Tumkur center is ~70km away (13.3379, 77.1173) -> NOT in Bangalore Zone
        assertThat(adminZoneService.isLocationInZone(13.3379, 77.1173, null, bangaloreZone)).isFalse();

        // But IS in Tumkur Zone
        assertThat(adminZoneService.isLocationInZone(13.3379, 77.1173, null, tumkurZone)).isTrue();
    }
}
