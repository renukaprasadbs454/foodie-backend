package com.foodie.delivery;

import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.common.enums.DeliveryDocType;
import com.foodie.common.enums.VehicleType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.delivery.dto.request.DeliveryZoneVerificationRequestDto;
import com.foodie.delivery.dto.response.DeliveryZoneVerificationResponseDto;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerDocument;
import com.foodie.delivery.mapper.DeliveryMapper;
import com.foodie.delivery.repository.DeliveryPartnerDocumentRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.service.impl.DeliveryServiceImpl;
import com.foodie.infrastructure.storage.ObjectStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryZoneVerificationTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryPartnerDocumentRepository deliveryPartnerDocumentRepository;

    @Mock
    private LocationZoneRepository locationZoneRepository;

    @Mock
    private ObjectStorageClient objectStorageClient;

    @Spy
    private DeliveryMapper deliveryMapper = new DeliveryMapper();

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private UUID userCredentialId;
    private DeliveryPartner partner;
    private LocationZone activeRadiusZone;
    private LocationZone activePolygonZone;
    private LocationZone inactiveZone;

    @BeforeEach
    void setUp() {
        deliveryService.setLocationZoneRepository(locationZoneRepository);
        userCredentialId = UUID.randomUUID();
        partner = DeliveryPartner.create(userCredentialId, "Test Driver", VehicleType.BIKE, "KA01AB1234");

        // Tumkur Central: lat 13.3379, lng 77.1173, radius 15.0 km
        activeRadiusZone = new LocationZone();
        activeRadiusZone.setId("dz-tumkur");
        activeRadiusZone.setZoneName("Tumkur Central");
        activeRadiusZone.setCityName("Bangalore");
        activeRadiusZone.setLatitude(13.3379);
        activeRadiusZone.setLongitude(77.1173);
        activeRadiusZone.setRadiusKm(15.0);
        activeRadiusZone.setStatus("ACTIVE");
        activeRadiusZone.setDeliveryPartnerEnabled(true);
        activeRadiusZone.setRestaurantEnabled(true);

        // Indiranagar Polygon Zone (triangle around 12.9784, 77.6408)
        activePolygonZone = new LocationZone();
        activePolygonZone.setId("dz-indiranagar");
        activePolygonZone.setZoneName("Indiranagar Tech Corridor");
        activePolygonZone.setCityName("Bangalore");
        activePolygonZone.setPolygonCoordinates("12.9700,77.6300 | 12.9900,77.6300 | 12.9800,77.6600");
        activePolygonZone.setStatus("ACTIVE");
        activePolygonZone.setDeliveryPartnerEnabled(true);
        activePolygonZone.setRestaurantEnabled(true);

        // Inactive Zone
        inactiveZone = new LocationZone();
        inactiveZone.setId("dz-inactive");
        inactiveZone.setZoneName("Old Inactive Zone");
        inactiveZone.setLatitude(13.0000);
        inactiveZone.setLongitude(77.0000);
        inactiveZone.setRadiusKm(10.0);
        inactiveZone.setStatus("INACTIVE");
        inactiveZone.setDeliveryPartnerEnabled(true);
    }

    @Test
    void verifyCoordinates_insideRadiusZone_returnsVerifiedTrue() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(activeRadiusZone));

        // Location 2 km from Tumkur center
        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(13.3400, 77.1200);

        assertThat(result.verified()).isTrue();
        assertThat(result.zoneId()).isEqualTo("dz-tumkur");
        assertThat(result.zoneName()).isEqualTo("Tumkur Central");
        assertThat(result.message()).isEqualTo("Location Verified — Tumkur Central");
    }

    @Test
    void verifyCoordinates_insidePolygonZone_returnsVerifiedTrue() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(activePolygonZone));

        // Location inside polygon
        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(12.9800, 77.6400);

        assertThat(result.verified()).isTrue();
        assertThat(result.zoneId()).isEqualTo("dz-indiranagar");
        assertThat(result.zoneName()).isEqualTo("Indiranagar Tech Corridor");
        assertThat(result.message()).isEqualTo("Location Verified — Indiranagar Tech Corridor");
    }

    @Test
    void verifyCoordinates_onZoneBoundary_returnsVerifiedTrue() {
        LocationZone boundaryZone = new LocationZone();
        boundaryZone.setId("dz-boundary");
        boundaryZone.setZoneName("Boundary Zone");
        boundaryZone.setLatitude(13.0);
        boundaryZone.setLongitude(77.0);
        boundaryZone.setRadiusKm(10.0);
        boundaryZone.setStatus("ACTIVE");
        boundaryZone.setDeliveryPartnerEnabled(true);

        when(locationZoneRepository.findAll()).thenReturn(List.of(boundaryZone));

        // Exactly on center or within radius
        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(13.0, 77.0);

        assertThat(result.verified()).isTrue();
        assertThat(result.zoneName()).isEqualTo("Boundary Zone");
    }

    @Test
    void verifyCoordinates_outsideEveryZone_returnsVerifiedFalse() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(activeRadiusZone, activePolygonZone));

        // Far away coordinates (e.g. Delhi coords when zones are in Bangalore)
        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(28.6139, 77.2090);

        assertThat(result.verified()).isFalse();
        assertThat(result.zoneId()).isNull();
        assertThat(result.zoneName()).isNull();
        assertThat(result.message()).isEqualTo("You are outside the delivery zone. Please move to an active delivery zone and try again.");
    }

    @Test
    void verifyCoordinates_noActiveZones_returnsVerifiedFalse() {
        when(locationZoneRepository.findAll()).thenReturn(Collections.emptyList());

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(13.3379, 77.1173);

        assertThat(result.verified()).isFalse();
        assertThat(result.message()).isEqualTo("You are outside the delivery zone. Please move to an active delivery zone and try again.");
    }

    @Test
    void verifyCoordinates_inactiveZoneIgnored_returnsVerifiedFalse() {
        when(locationZoneRepository.findAll()).thenReturn(List.of(inactiveZone));

        // Location matches inactive zone coords, but zone is INACTIVE
        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(13.0000, 77.0000);

        assertThat(result.verified()).isFalse();
        assertThat(result.message()).isEqualTo("You are outside the delivery zone. Please move to an active delivery zone and try again.");
    }

    @Test
    void verifyCoordinates_deliveryPartnerDisabledIgnored_returnsVerifiedFalse() {
        activeRadiusZone.setDeliveryPartnerEnabled(false);
        when(locationZoneRepository.findAll()).thenReturn(List.of(activeRadiusZone));

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyCoordinates(13.3379, 77.1173);

        assertThat(result.verified()).isFalse();
    }

    @Test
    void verifyZone_updatesPartnerWhenInsideZone() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findAll()).thenReturn(List.of(activeRadiusZone));

        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                13.3379, 77.1173, "MG Road, Tumkur");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isTrue();
        assertThat(partner.isZoneVerified()).isTrue();
        assertThat(partner.getZoneId()).isEqualTo("dz-tumkur");
        assertThat(partner.getZoneName()).isEqualTo("Tumkur Central");
        assertThat(partner.getLatitude()).isEqualTo(13.3379);
        assertThat(partner.getLongitude()).isEqualTo(77.1173);
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void verifyZone_invalidatesPartnerWhenOutsideZone() {
        partner.verifyZone("dz-tumkur", "Tumkur Central", 13.3379, 77.1173);
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findAll()).thenReturn(List.of(activeRadiusZone));

        // Move to outside coordinates
        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                28.6139, 77.2090, "Connaught Place, Delhi");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isFalse();
        assertThat(partner.isZoneVerified()).isFalse();
        assertThat(partner.getZoneId()).isNull();
        assertThat(partner.getZoneName()).isNull();
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void uploadDocument_allowsUploadWhenZoneNotVerified() {
        partner.setZoneVerified(false);
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        DeliveryPartnerDocument mockDoc = DeliveryPartnerDocument.create(partner, DeliveryDocType.LICENSE, "key-123");
        when(deliveryPartnerDocumentRepository.findByDeliveryPartnerIdAndDocType(any(), any()))
                .thenReturn(Optional.of(mockDoc));
        when(deliveryPartnerDocumentRepository.save(any()))
                .thenReturn(mockDoc);

        MockMultipartFile file = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes());

        var doc = deliveryService.uploadDocument(userCredentialId, DeliveryDocType.LICENSE, file);
        assertThat(doc).isNotNull();
    }

    @Test
    void uploadDocument_allowsUploadEvenIfCoordinatesOutsideActiveZone() {
        // Partner is outside zone or zone unverified, document upload is still permitted
        partner.verifyZone("dz-tumkur", "Tumkur Central", 28.6139, 77.2090); // Outside coords!
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        DeliveryPartnerDocument mockDoc = DeliveryPartnerDocument.create(partner, DeliveryDocType.LICENSE, "key-123");
        when(deliveryPartnerDocumentRepository.findByDeliveryPartnerIdAndDocType(any(), any()))
                .thenReturn(Optional.of(mockDoc));
        when(deliveryPartnerDocumentRepository.save(any()))
                .thenReturn(mockDoc);

        MockMultipartFile file = new MockMultipartFile(
                "file", "license.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes());

        var doc = deliveryService.uploadDocument(userCredentialId, DeliveryDocType.LICENSE, file);
        assertThat(doc).isNotNull();
    }

    @Test
    void verifyZone_withSelectedZone_insideSelectedZone_returnsVerifiedTrue() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findById("dz-tumkur")).thenReturn(Optional.of(activeRadiusZone));

        // Coordinates inside Tumkur
        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                13.3400, 77.1200, "MG Road, Tumkur", "dz-tumkur");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isTrue();
        assertThat(result.zoneId()).isEqualTo("dz-tumkur");
        assertThat(result.zoneName()).isEqualTo("Tumkur Central");
        assertThat(result.message()).isEqualTo("Location Verified — Tumkur Central");
        assertThat(partner.isZoneVerified()).isTrue();
        assertThat(partner.getZoneId()).isEqualTo("dz-tumkur");
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void verifyZone_withSelectedZone_outsideSelectedZone_returnsVerifiedFalse() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findById("dz-tumkur")).thenReturn(Optional.of(activeRadiusZone));

        // Location in Bangalore/Indiranagar (outside Tumkur)
        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                12.9800, 77.6400, "Indiranagar, Bangalore", "dz-tumkur");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isFalse();
        assertThat(result.zoneId()).isNull();
        assertThat(result.message()).isEqualTo("You are outside the selected delivery zone. Please move into the zone and try again.");
        assertThat(partner.isZoneVerified()).isFalse();
        assertThat(partner.getZoneId()).isNull();
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void verifyZone_switchesSelectedZone_revalidatesAgainstNewZone() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findById("dz-indiranagar")).thenReturn(Optional.of(activePolygonZone));

        // Partner selects Indiranagar with coords inside Indiranagar
        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                12.9800, 77.6400, "100ft Road, Indiranagar", "dz-indiranagar");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isTrue();
        assertThat(result.zoneId()).isEqualTo("dz-indiranagar");
        assertThat(result.zoneName()).isEqualTo("Indiranagar Tech Corridor");
        assertThat(result.message()).isEqualTo("Location Verified — Indiranagar Tech Corridor");
        assertThat(partner.isZoneVerified()).isTrue();
        assertThat(partner.getZoneId()).isEqualTo("dz-indiranagar");
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void verifyZone_withSelectedZone_whenZoneInactive_returnsVerifiedFalse() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(locationZoneRepository.findById("dz-inactive")).thenReturn(Optional.of(inactiveZone));

        DeliveryZoneVerificationRequestDto request = new DeliveryZoneVerificationRequestDto(
                13.0000, 77.0000, "Old Zone Area", "dz-inactive");

        DeliveryZoneVerificationResponseDto result = deliveryService.verifyZone(userCredentialId, request);

        assertThat(result.verified()).isFalse();
        assertThat(result.message()).isEqualTo("You are outside the selected delivery zone. Please move into the zone and try again.");
        assertThat(partner.isZoneVerified()).isFalse();
    }
}
