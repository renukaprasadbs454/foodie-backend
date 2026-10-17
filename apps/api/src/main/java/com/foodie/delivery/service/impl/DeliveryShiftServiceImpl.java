package com.foodie.delivery.service.impl;

import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.request.UpdateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftBookingResponseDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.entity.DeliveryShift;
import com.foodie.delivery.entity.DeliveryShiftBooking;
import com.foodie.delivery.repository.DeliveryShiftBookingRepository;
import com.foodie.delivery.repository.DeliveryShiftRepository;
import com.foodie.delivery.service.DeliveryShiftService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryShiftServiceImpl implements DeliveryShiftService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryShiftServiceImpl.class);

    private final DeliveryShiftRepository shiftRepository;
    private final DeliveryShiftBookingRepository bookingRepository;

    public DeliveryShiftServiceImpl(
            DeliveryShiftRepository shiftRepository,
            DeliveryShiftBookingRepository bookingRepository
    ) {
        this.shiftRepository = shiftRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    @Transactional
    public DeliveryShiftResponseDto createShift(CreateShiftRequestDto request) {
        validateShiftInput(request.shiftName(), request.startTime(), request.endTime());

        String shiftName = request.shiftName().trim();
        String startTime = request.startTime().trim();
        String endTime = request.endTime().trim();

        if (shiftRepository.existsByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
                shiftName, startTime, endTime, "DEACTIVATED")) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED,
                    "An active shift with name '" + shiftName + "' (" + startTime + " – " + endTime + ") already exists."
            );
        }

        String duration = calculateDuration(startTime, endTime);

        DeliveryShift shift = new DeliveryShift(
                shiftName,
                startTime,
                endTime,
                duration,
                "ACTIVE"
        );

        DeliveryShift saved = shiftRepository.save(shift);
        log.info("Created new delivery shift: id={}, name='{}', times='{} - {}', duration='{}'",
                saved.getId(), saved.getShiftName(), saved.getStartTime(), saved.getEndTime(), saved.getDurationHours());

        return mapToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryShiftResponseDto> getAllAdminShifts() {
        return shiftRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(s -> !"DEACTIVATED".equalsIgnoreCase(s.getStatus()))
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryShiftResponseDto> getAvailableShifts() {
        return shiftRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public DeliveryShiftResponseDto updateShift(UUID id, UpdateShiftRequestDto request) {
        DeliveryShift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Shift not found with id: " + id));

        validateShiftInput(request.shiftName(), request.startTime(), request.endTime());

        String shiftName = request.shiftName().trim();
        String startTime = request.startTime().trim();
        String endTime = request.endTime().trim();
        String duration = calculateDuration(startTime, endTime);
        String status = (request.status() != null && !request.status().isBlank())
                ? request.status().trim().toUpperCase()
                : shift.getStatus();

        shift.setShiftName(shiftName);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setDurationHours(duration);
        shift.setStatus(status);

        DeliveryShift saved = shiftRepository.save(shift);
        log.info("Updated delivery shift: id={}, status='{}'", saved.getId(), saved.getStatus());

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void deleteShift(UUID id) {
        DeliveryShift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Shift not found with id: " + id));

        shift.setStatus("DEACTIVATED");
        shiftRepository.save(shift);
        log.info("Deactivated/deleted delivery shift: id={}, name='{}'", id, shift.getShiftName());
    }

    @Override
    @Transactional
    public DeliveryShiftBookingResponseDto bookShift(UUID partnerId, UUID shiftId) {
        if (partnerId == null) {
            throw new BadRequestException(ErrorCode.UNAUTHORIZED, "Valid delivery partner identity required to book shifts.");
        }

        DeliveryShift shift = shiftRepository.findByIdAndStatus(shiftId, "ACTIVE")
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.VALIDATION_FAILED,
                        "Shift is not available for booking or has expired."
                ));

        boolean alreadyBooked = bookingRepository.existsByDeliveryPartnerIdAndShiftIdAndStatus(
                partnerId, shiftId, "BOOKED"
        );
        if (alreadyBooked) {
            throw new BadRequestException(
                    ErrorCode.VALIDATION_FAILED,
                    "You have already booked this shift ('" + shift.getShiftName() + "')."
            );
        }

        DeliveryShiftBooking booking = new DeliveryShiftBooking(
                partnerId,
                shiftId,
                shift.getShiftName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getDurationHours(),
                "BOOKED",
                Instant.now()
        );

        DeliveryShiftBooking saved = bookingRepository.save(booking);
        log.info("Delivery partner {} successfully booked shift {} ('{}')",
                partnerId, shiftId, shift.getShiftName());

        return mapBookingToDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryShiftBookingResponseDto> getMyBookings(UUID partnerId) {
        if (partnerId == null) {
            return List.of();
        }
        return bookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId)
                .stream()
                .map(this::mapBookingToDto)
                .toList();
    }

    private void validateShiftInput(String shiftName, String startTime, String endTime) {
        if (shiftName == null || shiftName.trim().isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Shift name is required.");
        }
        if (startTime == null || startTime.trim().isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Start time is required.");
        }
        if (endTime == null || endTime.trim().isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "End time is required.");
        }

        int startMin = parseTimeToMinutes(startTime);
        int endMin = parseTimeToMinutes(endTime);

        if (startMin < 0) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid start time format: '" + startTime + "'. Example: '11:00 PM' or '23:00'.");
        }
        if (endMin < 0) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "Invalid end time format: '" + endTime + "'. Example: '2:00 AM' or '02:00'.");
        }
        if (startMin == endMin) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "End time must be after the start time (cannot be equal).");
        }
    }

    public static String calculateDuration(String startStr, String endStr) {
        int startMin = parseTimeToMinutes(startStr);
        int endMin = parseTimeToMinutes(endStr);
        if (startMin < 0 || endMin < 0) {
            return "Custom";
        }
        int diff = endMin - startMin;
        if (diff <= 0) {
            diff += 24 * 60; // crossed midnight
        }
        int hours = diff / 60;
        int mins = diff % 60;
        if (mins == 0) {
            return hours + "h";
        }
        return hours + "h " + mins + "m";
    }

    public static int parseTimeToMinutes(String timeStr) {
        if (timeStr == null) return -1;
        String clean = timeStr.trim().toUpperCase();
        boolean isPm = clean.endsWith("PM");
        boolean isAm = clean.endsWith("AM");
        clean = clean.replace("PM", "").replace("AM", "").trim();

        String[] parts = clean.split(":");
        int hours;
        int minutes = 0;
        try {
            hours = Integer.parseInt(parts[0].trim());
            if (parts.length > 1) {
                minutes = Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException e) {
            return -1;
        }

        if (hours < 0 || minutes < 0 || minutes >= 60) {
            return -1;
        }

        if (isPm) {
            if (hours < 12) hours += 12;
        } else if (isAm) {
            if (hours == 12) hours = 0;
        }
        return (hours * 60 + minutes) % (24 * 60);
    }

    private DeliveryShiftResponseDto mapToDto(DeliveryShift s) {
        return new DeliveryShiftResponseDto(
                s.getId(),
                s.getShiftName(),
                s.getStartTime(),
                s.getEndTime(),
                s.getDurationHours(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private DeliveryShiftBookingResponseDto mapBookingToDto(DeliveryShiftBooking b) {
        return new DeliveryShiftBookingResponseDto(
                b.getId(),
                b.getDeliveryPartnerId(),
                b.getShiftId(),
                b.getShiftName(),
                b.getStartTime(),
                b.getEndTime(),
                b.getDurationHours(),
                b.getStatus(),
                b.getBookedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
