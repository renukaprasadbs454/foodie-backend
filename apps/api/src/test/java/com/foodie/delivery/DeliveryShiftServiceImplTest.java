package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.common.exception.BadRequestException;
import com.foodie.delivery.dto.request.CreateShiftRequestDto;
import com.foodie.delivery.dto.response.DeliveryShiftResponseDto;
import com.foodie.delivery.entity.DeliveryShift;
import com.foodie.delivery.repository.DeliveryShiftBookingRepository;
import com.foodie.delivery.repository.DeliveryShiftRepository;
import com.foodie.delivery.service.impl.DeliveryShiftServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeliveryShiftServiceImplTest {

    @Mock
    private DeliveryShiftRepository shiftRepository;

    @Mock
    private DeliveryShiftBookingRepository bookingRepository;

    private DeliveryShiftServiceImpl shiftService;

    @BeforeEach
    void setUp() {
        shiftService = new DeliveryShiftServiceImpl(shiftRepository, bookingRepository);
    }

    @Test
    @DisplayName("Should successfully create delivery shift with calculated duration")
    void createShift_success() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Morning Rush",
                "06:00 AM",
                "12:00 PM"
        );

        when(shiftRepository.existsByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
                "Morning Rush", "06:00 AM", "12:00 PM", "DEACTIVATED"))
                .thenReturn(false);

        when(shiftRepository.save(any(DeliveryShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryShiftResponseDto response = shiftService.createShift(request);

        assertThat(response).isNotNull();
        assertThat(response.shiftName()).isEqualTo("Morning Rush");
        assertThat(response.startTime()).isEqualTo("06:00 AM");
        assertThat(response.endTime()).isEqualTo("12:00 PM");
        assertThat(response.durationHours()).isEqualTo("6h");
        assertThat(response.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<DeliveryShift> captor = ArgumentCaptor.forClass(DeliveryShift.class);
        verify(shiftRepository).save(captor.capture());
        DeliveryShift saved = captor.getValue();
        assertThat(saved.getShiftName()).isEqualTo("Morning Rush");
        assertThat(saved.getDurationHours()).isEqualTo("6h");
    }

    @Test
    @DisplayName("Should calculate duration accurately across midnight boundary")
    void createShift_crossMidnight() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Late Night Slot",
                "11:00 PM",
                "03:00 AM"
        );

        when(shiftRepository.save(any(DeliveryShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryShiftResponseDto response = shiftService.createShift(request);

        assertThat(response.durationHours()).isEqualTo("4h");
    }

    @Test
    @DisplayName("Should throw BadRequestException if shift name is blank")
    void createShift_emptyName_throwsBadRequest() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "   ",
                "06:00 AM",
                "12:00 PM"
        );

        assertThatThrownBy(() -> shiftService.createShift(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Shift name is required");
    }

    @Test
    @DisplayName("Should throw BadRequestException if start time and end time are equal")
    void createShift_equalTimes_throwsBadRequest() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Same Time Shift",
                "10:00 AM",
                "10:00 AM"
        );

        assertThatThrownBy(() -> shiftService.createShift(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be equal");
    }

    @Test
    @DisplayName("Should throw BadRequestException if duplicate active shift already exists")
    void createShift_duplicate_throwsBadRequest() {
        CreateShiftRequestDto request = new CreateShiftRequestDto(
                "Morning Rush",
                "06:00 AM",
                "12:00 PM"
        );

        when(shiftRepository.existsByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
                "Morning Rush", "06:00 AM", "12:00 PM", "DEACTIVATED"))
                .thenReturn(true);

        assertThatThrownBy(() -> shiftService.createShift(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should retrieve all active admin shifts")
    void getAllAdminShifts_returnsActiveShifts() {
        DeliveryShift s1 = new DeliveryShift("Shift 1", "06:00 AM", "12:00 PM", "6h", "ACTIVE");
        DeliveryShift s2 = new DeliveryShift("Shift 2", "12:00 PM", "06:00 PM", "6h", "ACTIVE");
        DeliveryShift s3 = new DeliveryShift("Deactivated Shift", "06:00 PM", "10:00 PM", "4h", "DEACTIVATED");

        when(shiftRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2, s3));

        List<DeliveryShiftResponseDto> list = shiftService.getAllAdminShifts();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).shiftName()).isEqualTo("Shift 1");
        assertThat(list.get(1).shiftName()).isEqualTo("Shift 2");
    }

    @Test
    @DisplayName("Zone Isolation: Shifts created for a specific zone are isolated to that zone")
    void createShift_withZone_assignsZoneAndPermitsDuplicateAcrossDifferentZones() {
        CreateShiftRequestDto bangaloreRequest = new CreateShiftRequestDto(
                "Peak Rush", "06:00 PM", "11:00 PM", "dz-bangalore", "Bangalore Zone"
        );

        when(shiftRepository.save(any(DeliveryShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryShiftResponseDto bangaloreShift = shiftService.createShift(bangaloreRequest);
        assertThat(bangaloreShift.zoneId()).isEqualTo("dz-bangalore");
        assertThat(bangaloreShift.zoneName()).isEqualTo("Bangalore Zone");

        // Same shift name in a DIFFERENT zone (Tumkur) is allowed and not blocked by Bangalore shift
        CreateShiftRequestDto tumkurRequest = new CreateShiftRequestDto(
                "Peak Rush", "06:00 PM", "11:00 PM", "dz-tumkur", "Tumkur Zone"
        );
        DeliveryShift existingBangaloreShift = new DeliveryShift("Peak Rush", "06:00 PM", "11:00 PM", "5h", "ACTIVE", "dz-bangalore", "Bangalore Zone");
        when(shiftRepository.findByShiftNameIgnoreCaseAndStartTimeAndEndTimeAndStatusNot(
                "Peak Rush", "06:00 PM", "11:00 PM", "DEACTIVATED"))
                .thenReturn(List.of(existingBangaloreShift));

        DeliveryShiftResponseDto tumkurShift = shiftService.createShift(tumkurRequest);
        assertThat(tumkurShift.zoneId()).isEqualTo("dz-tumkur");
        assertThat(tumkurShift.zoneName()).isEqualTo("Tumkur Zone");
    }

    @Test
    @DisplayName("Zone Visibility: Delivery partners in Tumkur only receive shifts available in Tumkur zone")
    void getAvailableShiftsForZone_onlyReturnsShiftsMatchingPartnerZone() {
        DeliveryShift blrShift = new DeliveryShift("BLR Morning", "06:00 AM", "12:00 PM", "6h", "ACTIVE", "dz-bangalore", "Bangalore Zone");
        DeliveryShift tmkShift = new DeliveryShift("TMK Morning", "07:00 AM", "01:00 PM", "6h", "ACTIVE", "dz-tumkur", "Tumkur Zone");
        DeliveryShift universalShift = new DeliveryShift("Universal Express", "12:00 PM", "05:00 PM", "5h", "ACTIVE", null, null);

        when(shiftRepository.findByStatusOrderByCreatedAtDesc("ACTIVE"))
                .thenReturn(List.of(blrShift, tmkShift, universalShift));

        // Delivery Partner in Tumkur queries available shifts
        List<DeliveryShiftResponseDto> tumkurAvailable = shiftService.getAvailableShiftsForZone("dz-tumkur");
        assertThat(tumkurAvailable).extracting(DeliveryShiftResponseDto::shiftName)
                .contains("TMK Morning", "Universal Express")
                .doesNotContain("BLR Morning");

        // Delivery Partner in Bangalore queries available shifts
        List<DeliveryShiftResponseDto> blrAvailable = shiftService.getAvailableShiftsForZone("dz-bangalore");
        assertThat(blrAvailable).extracting(DeliveryShiftResponseDto::shiftName)
                .contains("BLR Morning", "Universal Express")
                .doesNotContain("TMK Morning");
    }
}
