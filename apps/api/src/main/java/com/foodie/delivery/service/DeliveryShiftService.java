package com.foodie.delivery.service;

import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.request.UpdateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftBookingResponseDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import java.util.List;
import java.util.UUID;

public interface DeliveryShiftService {

    DeliveryShiftResponseDto createShift(CreateShiftRequestDto request);

    List<DeliveryShiftResponseDto> getAllAdminShifts();

    List<DeliveryShiftResponseDto> getAvailableShifts();

    DeliveryShiftResponseDto updateShift(UUID id, UpdateShiftRequestDto request);

    void deleteShift(UUID id);

    DeliveryShiftBookingResponseDto bookShift(UUID partnerId, UUID shiftId);

    List<DeliveryShiftBookingResponseDto> getMyBookings(UUID partnerId);
}
