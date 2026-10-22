package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.admin.controller.AdminShiftController;
import com.foodie.admin.entity.AdminRoleName;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.entity.Role;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.security.AdminAccess;
import com.foodie.common.dto.ApiResponse;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.request.UpdateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.entity.DeliveryShift;
import com.foodie.delivery.repository.DeliveryShiftRepository;
import com.foodie.delivery.service.DeliveryShiftService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminShiftControllerTest {

    @Mock
    private DeliveryShiftService shiftService;

    @Mock
    private AdminAccess adminAccess;

    @Mock
    private DeliveryShiftRepository shiftRepository;

    @Mock
    private LocationZoneRepository locationZoneRepository;

    @Mock
    private Authentication authentication;

    private AdminShiftController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminShiftController(shiftService, adminAccess, shiftRepository, locationZoneRepository);
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts - Should create shift and return 201 Created")
    void createShift_returnsCreatedStatus() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Evening Peak",
                "05:00 PM",
                "10:00 PM"
        );

        DeliveryShiftResponseDto createdDto = new DeliveryShiftResponseDto(
                UUID.randomUUID(),
                "Evening Peak",
                "05:00 PM",
                "10:00 PM",
                "5h",
                "ACTIVE",
                Instant.now(),
                Instant.now()
        );

        when(shiftService.createShift(any(CreateShiftRequestDto.class))).thenReturn(createdDto);

        ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> response = controller.createShift(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().shiftName()).isEqualTo("Evening Peak");
        assertThat(response.getBody().data().durationHours()).isEqualTo("5h");
        verify(shiftService).createShift(any(CreateShiftRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/v1/admin/shifts - Zone Admin: Automatically assigns to admin's assigned zone with dynamic zone name lookup")
    void createShift_zoneAdmin_automaticallyAssignsZone() {
        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.OPS);
        AdminUser tumkurAdmin = AdminUser.create(UUID.randomUUID(), role, "Tumkur Ops Admin", "dz-tumkur");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(adminAccess.requireAdmin(authentication)).thenReturn(tumkurAdmin);

        LocationZone tmkZone = new LocationZone();
        tmkZone.setId("dz-tumkur");
        tmkZone.setZoneName("Tumkur Zone");
        when(locationZoneRepository.findById("dz-tumkur")).thenReturn(Optional.of(tmkZone));

        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Tumkur Night Shift",
                "08:00 PM",
                "12:00 AM",
                null,
                null
        );

        DeliveryShiftResponseDto createdDto = new DeliveryShiftResponseDto(
                UUID.randomUUID(),
                "Tumkur Night Shift",
                "08:00 PM",
                "12:00 AM",
                "4h",
                "ACTIVE",
                "dz-tumkur",
                "Tumkur Zone",
                Instant.now(),
                Instant.now()
        );

        when(shiftService.createShift(argThat(dto ->
                "dz-tumkur".equals(dto.zoneId()) && "Tumkur Zone".equals(dto.zoneName())
        ))).thenReturn(createdDto);

        ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> response = controller.createShift(
                authentication, null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().zoneId()).isEqualTo("dz-tumkur");
        assertThat(response.getBody().data().zoneName()).isEqualTo("Tumkur Zone");
    }

    @Test
    @DisplayName("GET /api/v1/admin/shifts - Zone Admin: Returns only shifts for assigned zone")
    void getAllShifts_zoneAdmin_restrictedToZone() {
        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.OPS);
        AdminUser tumkurAdmin = AdminUser.create(UUID.randomUUID(), role, "Tumkur Ops Admin", "dz-tumkur");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(adminAccess.requireAdmin(authentication)).thenReturn(tumkurAdmin);

        DeliveryShiftResponseDto s1 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Tumkur Morning", "06:00 AM", "12:00 PM", "6h", "ACTIVE",
                "dz-tumkur", "Tumkur Zone", Instant.now(), Instant.now()
        );
        when(shiftService.getAllAdminShifts("dz-tumkur")).thenReturn(List.of(s1));

        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> response = controller.getAllShifts(
                authentication, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).zoneId()).isEqualTo("dz-tumkur");
        verify(shiftService).getAllAdminShifts("dz-tumkur");
    }

    @Test
    @DisplayName("GET /api/v1/admin/shifts - Super Admin: Can view all shifts or filter by specific zone")
    void getAllShifts_superAdmin_supportsGlobalAndZoneFilter() {
        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.SUPER_ADMIN);
        AdminUser superAdmin = AdminUser.create(UUID.randomUUID(), role, "Super Admin", null);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(adminAccess.requireAdmin(authentication)).thenReturn(superAdmin);
        when(adminAccess.resolveAuthorizedZone(authentication, "dz-tumkur")).thenReturn("dz-tumkur");
        when(adminAccess.resolveAuthorizedZone(authentication, "ALL")).thenReturn("ALL");

        DeliveryShiftResponseDto s1 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Tumkur Shift", "06:00 AM", "12:00 PM", "6h", "ACTIVE",
                "dz-tumkur", "Tumkur Zone", Instant.now(), Instant.now()
        );
        DeliveryShiftResponseDto s2 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Bangalore Shift", "06:00 AM", "12:00 PM", "6h", "ACTIVE",
                "dz-bangalore", "Bangalore Zone", Instant.now(), Instant.now()
        );

        when(shiftService.getAllAdminShifts("dz-tumkur")).thenReturn(List.of(s1));
        when(shiftService.getAllAdminShifts()).thenReturn(List.of(s1, s2));

        // 1. Filtered by zone: dz-tumkur
        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> filteredResp = controller.getAllShifts(
                authentication, null, "dz-tumkur");
        assertThat(filteredResp.getBody().data()).hasSize(1);
        assertThat(filteredResp.getBody().data().get(0).zoneId()).isEqualTo("dz-tumkur");

        // 2. Global view: ALL
        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> globalResp = controller.getAllShifts(
                authentication, null, "ALL");
        assertThat(globalResp.getBody().data()).hasSize(2);
    }

    @Test
    @DisplayName("DELETE /api/v1/admin/shifts/{id} - Zone Admin cannot delete shift in another zone")
    void deleteShift_zoneAdmin_crossZoneRejected() {
        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.OPS);
        AdminUser tumkurAdmin = AdminUser.create(UUID.randomUUID(), role, "Tumkur Ops Admin", "dz-tumkur");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(adminAccess.requireAdmin(authentication)).thenReturn(tumkurAdmin);

        UUID shiftId = UUID.randomUUID();
        DeliveryShift bangaloreShift = new DeliveryShift("BLR Shift", "06:00 AM", "12:00 PM", "6h", "ACTIVE", "dz-bangalore", "Bangalore Zone");
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(bangaloreShift));

        assertThatThrownBy(() -> controller.deleteShift(authentication, null, shiftId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Unauthorized cross-zone delete");
    }

    @Test
    @DisplayName("PUT /api/v1/admin/shifts/{id} - Zone Admin cannot update shift in another zone")
    void updateShift_zoneAdmin_crossZoneRejected() {
        Role role = Role.ref(UUID.randomUUID(), AdminRoleName.OPS);
        AdminUser tumkurAdmin = AdminUser.create(UUID.randomUUID(), role, "Tumkur Ops Admin", "dz-tumkur");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(adminAccess.requireAdmin(authentication)).thenReturn(tumkurAdmin);

        UUID shiftId = UUID.randomUUID();
        DeliveryShift bangaloreShift = new DeliveryShift("BLR Shift", "06:00 AM", "12:00 PM", "6h", "ACTIVE", "dz-bangalore", "Bangalore Zone");
        when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(bangaloreShift));

        UpdateShiftRequestDto updateDto = new UpdateShiftRequestDto("BLR Shift Mod", "07:00 AM", "01:00 PM", "ACTIVE");

        assertThatThrownBy(() -> controller.updateShift(authentication, null, shiftId, updateDto))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Unauthorized cross-zone update");
    }
}
