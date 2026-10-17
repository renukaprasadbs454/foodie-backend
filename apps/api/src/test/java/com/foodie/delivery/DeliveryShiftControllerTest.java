package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.controller.DeliveryShiftController;
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
class DeliveryShiftControllerTest {

    @Mock
    private DeliveryShiftService shiftService;

    private DeliveryShiftController controller;

    @BeforeEach
    void setUp() {
        controller = new DeliveryShiftController(shiftService);
    }

    @Test
    @DisplayName("GET /api/v1/delivery/shifts/available - Should return available shifts for delivery partners")
    void getAvailableShifts_returnsOk() {
        DeliveryShiftResponseDto s1 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Afternoon Slot", "01:00 PM", "06:00 PM", "5h", "ACTIVE", Instant.now(), Instant.now()
        );
        when(shiftService.getAvailableShifts()).thenReturn(List.of(s1));

        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> response = controller.getAvailableShifts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).shiftName()).isEqualTo("Afternoon Slot");
        verify(shiftService).getAvailableShifts();
    }

    @Test
    @DisplayName("GET /api/v1/delivery/shifts - Alias should return available shifts for delivery partners")
    void getShifts_returnsOk() {
        DeliveryShiftResponseDto s1 = new DeliveryShiftResponseDto(
                UUID.randomUUID(), "Dinner Shift", "07:00 PM", "11:00 PM", "4h", "ACTIVE", Instant.now(), Instant.now()
        );
        when(shiftService.getAvailableShifts()).thenReturn(List.of(s1));

        ResponseEntity<ApiResponse<List<DeliveryShiftResponseDto>>> response = controller.getShifts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().get(0).shiftName()).isEqualTo("Dinner Shift");
        verify(shiftService).getAvailableShifts();
    }
}
