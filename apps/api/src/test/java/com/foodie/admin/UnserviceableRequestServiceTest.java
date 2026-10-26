package com.foodie.admin;

import com.foodie.admin.dto.UnserviceableRequestDto;
import com.foodie.admin.entity.UnserviceableRequest;
import com.foodie.admin.repository.UnserviceableRequestRepository;
import com.foodie.admin.service.UnserviceableRequestService;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnserviceableRequestServiceTest {

    @Mock
    private UnserviceableRequestRepository repository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    private UnserviceableRequestService service;

    @BeforeEach
    void setUp() {
        service = new UnserviceableRequestService(repository, deliveryPartnerRepository);
    }

    @Test
    void logDeliveryPartnerUnserviceableRequest_savesPendingRequestInDatabase() {
        UUID partnerId = UUID.randomUUID();
        when(repository.findFirstByDeliveryPartnerId(partnerId)).thenReturn(Optional.empty());
        when(repository.save(any(UnserviceableRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryPartner partner = mock(DeliveryPartner.class);
        when(deliveryPartnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));

        UnserviceableRequestDto result = service.logDeliveryPartnerUnserviceableRequest(
                partnerId.toString(),
                "John Rider",
                "+919876543210",
                "john@foodie.local",
                13.2430,
                77.7048,
                "Devanahalli, Bangalore",
                "dz-bangalore",
                "Bengaluru Zone",
                "Bangalore",
                List.of("IDENTITY", "LICENSE")
        );

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getRequestSource()).isEqualTo("DELIVERY_PARTNER");
        assertThat(result.getLatitude()).isEqualTo(13.2430);
        assertThat(result.getLongitude()).isEqualTo(77.7048);
        assertThat(result.getZoneId()).isEqualTo("dz-bangalore");
        assertThat(result.getZoneName()).isEqualTo("Bengaluru Zone");

        ArgumentCaptor<UnserviceableRequest> captor = ArgumentCaptor.forClass(UnserviceableRequest.class);
        verify(repository).save(captor.capture());
        UnserviceableRequest savedEntity = captor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo("PENDING");
        assertThat(savedEntity.getRequestSource()).isEqualTo("DELIVERY_PARTNER");
        assertThat(savedEntity.getDeliveryPartnerId()).isEqualTo(partnerId);

        verify(partner).setLocationApprovalStatus("PENDING");
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void updateStatus_approve_updatesDatabaseAndSetsDeliveryPartnerZoneVerified() {
        UUID partnerId = UUID.randomUUID();
        UnserviceableRequest entity = new UnserviceableRequest();
        entity.setId("req-dp-test1");
        entity.setDeliveryPartnerId(partnerId);
        entity.setRequestSource("DELIVERY_PARTNER");
        entity.setStatus("PENDING");
        entity.setZoneId("dz-bangalore");
        entity.setZoneName("Bengaluru Zone");

        when(repository.findById("req-dp-test1")).thenReturn(Optional.of(entity));
        when(repository.save(any(UnserviceableRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryPartner partner = mock(DeliveryPartner.class);
        when(deliveryPartnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));

        UnserviceableRequestDto result = service.updateStatus("req-dp-test1", "APPROVED");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("APPROVED");

        verify(repository).save(entity);
        assertThat(entity.getStatus()).isEqualTo("APPROVED");

        verify(partner).setZoneVerified(true);
        verify(partner).setLocationApprovalStatus("APPROVED");
        verify(partner).setZoneId("dz-bangalore");
        verify(partner).setZoneName("Bengaluru Zone");
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void updateStatus_reject_directConfirmation_updatesDatabaseAndSetsDeliveryPartnerNotVerified() {
        UUID partnerId = UUID.randomUUID();
        UnserviceableRequest entity = new UnserviceableRequest();
        entity.setId("req-dp-test2");
        entity.setDeliveryPartnerId(partnerId);
        entity.setRequestSource("DELIVERY_PARTNER");
        entity.setStatus("PENDING");

        when(repository.findById("req-dp-test2")).thenReturn(Optional.of(entity));
        when(repository.save(any(UnserviceableRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryPartner partner = mock(DeliveryPartner.class);
        when(deliveryPartnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));

        // Direct rejection without asking for rejection reason
        UnserviceableRequestDto result = service.updateStatus("req-dp-test2", "REJECTED");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REJECTED");

        verify(repository).save(entity);
        assertThat(entity.getStatus()).isEqualTo("REJECTED");

        verify(partner).setZoneVerified(false);
        verify(partner).setLocationApprovalStatus("REJECTED");
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void getAllRequests_returnsRealDatabaseRequests() {
        UnserviceableRequest req1 = new UnserviceableRequest();
        req1.setId("req-101");
        req1.setRequesterName("Partner Alice");
        req1.setRequestSource("DELIVERY_PARTNER");
        req1.setStatus("PENDING");
        req1.setCreatedAt(Instant.now());

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(req1));

        List<UnserviceableRequestDto> list = service.getAllRequests();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo("req-101");
        assertThat(list.get(0).getRequesterName()).isEqualTo("Partner Alice");
    }
}