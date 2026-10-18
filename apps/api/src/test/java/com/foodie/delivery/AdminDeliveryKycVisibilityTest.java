package com.foodie.delivery;

import com.foodie.admin.controller.AdminDeliveryController;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.enums.DeliveryDocType;
import com.foodie.common.enums.VehicleType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerDocument;
import com.foodie.delivery.mapper.DeliveryMapper;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerDocumentRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.service.DeliveryService;
import com.foodie.security.principal.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDeliveryKycVisibilityTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryPartnerDocumentRepository deliveryPartnerDocumentRepository;

    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private com.foodie.admin.service.AdminZoneService adminZoneService;

    @Spy
    private DeliveryMapper deliveryMapper = new DeliveryMapper();

    @InjectMocks
    private AdminDeliveryController adminDeliveryController;

    private UUID userCredentialId;
    private DeliveryPartner completePartner;
    private DeliveryPartner noZonePartner;
    private DeliveryPartner incompleteDocsPartner;

    @BeforeEach
    void setUp() {
        userCredentialId = UUID.randomUUID();

        // 1. Complete Partner: zone selected & verified, all 3 documents uploaded
        completePartner = DeliveryPartner.create(userCredentialId, "Rahul Verma", VehicleType.BIKE, "KA-01-AB-1234");
        completePartner.verifyZone("dz-tumkur", "Tumkur Central", 13.3408, 77.1010);

        // 2. No zone partner
        noZonePartner = DeliveryPartner.create(UUID.randomUUID(), "Incomplete Zone Driver", VehicleType.BIKE, "KA-01-CD-5678");

        // 3. Incomplete docs partner (has zone, but only 2 documents uploaded)
        incompleteDocsPartner = DeliveryPartner.create(UUID.randomUUID(), "Missing RC Driver", VehicleType.SCOOTER, "KA-01-EF-9012");
        incompleteDocsPartner.verifyZone("dz-tumkur", "Tumkur Central", 13.3408, 77.1010);
    }

    private List<DeliveryPartnerDocument> createCompleteDocuments(DeliveryPartner partner) {
        DeliveryPartnerDocument idDoc = DeliveryPartnerDocument.create(partner, DeliveryDocType.IDENTITY, "docs/identity.png");
        DeliveryPartnerDocument licDoc = DeliveryPartnerDocument.create(partner, DeliveryDocType.LICENSE, "docs/license.png");
        DeliveryPartnerDocument rcDoc = DeliveryPartnerDocument.create(partner, DeliveryDocType.VEHICLE_RC, "docs/rc.png");
        return List.of(idDoc, licDoc, rcDoc);
    }

    @Test
    void getPartner_whenKycOnboardingComplete_returnsPartnerDetails() {
        when(deliveryPartnerRepository.findById(completePartner.getId())).thenReturn(Optional.of(completePartner));
        when(deliveryPartnerDocumentRepository.findByDeliveryPartnerId(completePartner.getId()))
                .thenReturn(createCompleteDocuments(completePartner));

        ResponseEntity<?> response = adminDeliveryController.getPartner(completePartner.getId());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void getPartner_whenNoZoneSelected_throwsResourceNotFoundException() {
        when(deliveryPartnerRepository.findById(noZonePartner.getId())).thenReturn(Optional.of(noZonePartner));

        assertThatThrownBy(() -> adminDeliveryController.getPartner(noZonePartner.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("KYC onboarding requirements");
    }

    @Test
    void getPartner_whenMissingRequiredDocument_throwsResourceNotFoundException() {
        when(deliveryPartnerRepository.findById(incompleteDocsPartner.getId())).thenReturn(Optional.of(incompleteDocsPartner));
        // Only IDENTITY and LICENSE, missing VEHICLE_RC
        DeliveryPartnerDocument idDoc = DeliveryPartnerDocument.create(incompleteDocsPartner, DeliveryDocType.IDENTITY, "docs/identity.png");
        DeliveryPartnerDocument licDoc = DeliveryPartnerDocument.create(incompleteDocsPartner, DeliveryDocType.LICENSE, "docs/license.png");
        when(deliveryPartnerDocumentRepository.findByDeliveryPartnerId(incompleteDocsPartner.getId()))
                .thenReturn(List.of(idDoc, licDoc));

        assertThatThrownBy(() -> adminDeliveryController.getPartner(incompleteDocsPartner.getId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("KYC onboarding requirements");
    }

    @Test
    void approveKyc_whenIncompletePartner_throwsBadRequestException() {
        when(deliveryPartnerRepository.findById(noZonePartner.getId())).thenReturn(Optional.of(noZonePartner));

        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), com.foodie.common.enums.UserType.ADMIN);
        assertThatThrownBy(() -> adminDeliveryController.approveKyc(principal, noZonePartner.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must select a delivery zone and upload all required documents");
    }

    @Test
    void rejectKyc_whenIncompletePartner_throwsBadRequestException() {
        when(deliveryPartnerRepository.findById(incompleteDocsPartner.getId())).thenReturn(Optional.of(incompleteDocsPartner));
        when(deliveryPartnerDocumentRepository.findByDeliveryPartnerId(incompleteDocsPartner.getId()))
                .thenReturn(List.of(DeliveryPartnerDocument.create(incompleteDocsPartner, DeliveryDocType.IDENTITY, "docs/id.png")));

        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), com.foodie.common.enums.UserType.ADMIN);
        assertThatThrownBy(() -> adminDeliveryController.rejectKyc(principal, incompleteDocsPartner.getId(), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("has not completed KYC onboarding requirements");
    }

    @Test
    void getStats_usesFindAllKycReadyPartners() {
        when(deliveryPartnerRepository.findAllKycReadyPartners()).thenReturn(List.of(completePartner));

        ResponseEntity<?> response = adminDeliveryController.getStats(null, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
