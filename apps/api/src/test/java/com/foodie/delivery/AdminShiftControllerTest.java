package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.admin.controller.AdminShiftController;
import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.service.DeliveryShiftService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminShiftControllerTest {

    @Mock
    private DeliveryShiftService shiftService;

    private AdminShiftController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminShiftController(shiftService);
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

        when(shiftService.createShift(request)).thenReturn(createdDto);

        ResponseEntity<ApiResponse<DeliveryShiftResponseDto>> response = controller.createShift(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().shiftName()).isEqualTo("Evening Peak");
        assertThat(response.getBody().data().durationHours()).isEqualTo("5h");
        verify(shiftService).createShift(request);
    }

    @Test
    @DisplayName("GET /api/v1/admin/shifts - Should return list of all admin shifts")
    void getAllShifts_returnsOk() {
        DeliveryShiftResponseDto s1 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Morning", "06:00 AM", "12:00 PM", "6h", "ACTIVE", Instant.now(), Instant.now()
        );
        when(shiftService.getAllAdminShifts()).thenReturn(List.of(s1));

        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> response = controller.getAllShifts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).shiftName()).isEqualTo("Morning");
    }
}
