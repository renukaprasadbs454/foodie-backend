package com.foodie.delivery;

import com.foodie.common.enums.KycStatus;
import com.foodie.common.enums.VehicleType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.UnprocessableEntityException;
import com.foodie.delivery.config.DeliveryProperties;
import com.foodie.delivery.dto.request.SetAvailabilityRequestDto;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.mapper.DeliveryMapper;
import com.foodie.delivery.repository.*;
import com.foodie.delivery.service.DeliveryPricingService;
import com.foodie.delivery.service.PartnerGeoService;
import com.foodie.delivery.service.biometrics.FaceBiometricsService;
import com.foodie.delivery.service.biometrics.FaceBiometricsService.FaceVerificationResult;
import com.foodie.delivery.service.impl.DeliveryServiceImpl;
import com.foodie.infrastructure.storage.ObjectStorageClient;
import com.foodie.payment.repository.PaymentRepository;
import com.foodie.security.ratelimit.RedisRateLimiter;
import com.foodie.shared.contract.OrderDeliveryPort;
import com.foodie.shared.contract.RestaurantPickupQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryFaceVerificationTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryPartnerDocumentRepository deliveryPartnerDocumentRepository;

    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Mock
    private DeliveryLocationHistoryRepository deliveryLocationHistoryRepository;

    @Mock
    private DeliveryCashDepositRepository deliveryCashDepositRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ObjectStorageClient objectStorageClient;

    @Mock
    private PartnerGeoService partnerGeoService;

    @Mock
    private OrderDeliveryPort orderDeliveryPort;

    @Mock
    private RestaurantPickupQuery restaurantPickupQuery;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private DeliveryProperties deliveryProperties;

    @Mock
    private DeliveryPricingService deliveryPricingService;

    @Mock
    private FaceBiometricsService faceBiometricsService;

    @Spy
    private DeliveryMapper deliveryMapper = new DeliveryMapper();

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private UUID userCredentialId;
    private DeliveryPartner partner;

    @BeforeEach
    void setUp() {
        userCredentialId = UUID.randomUUID();
        partner = DeliveryPartner.create(userCredentialId, "Rahul Sharma", VehicleType.BIKE, "MH02CD5678");
        lenient().when(faceBiometricsService.validateFaceSelfie(any()))
                .thenReturn(new FaceBiometricsService.FaceDetectionResult(true, new FaceBiometricsService.Rectangle(10, 10, 50, 50), 0.90, "Face detected", null, 1));
    }

    @Test
    @DisplayName("Should reject face verification if partner KYC is not approved")
    void testVerifyFace_ThrowsWhenKycNotVerified() {
        partner.setProfileImageKey("delivery-partners/1/profile/selfie.jpg");
        // KYC status is PENDING by default

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));

        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> deliveryService.verifyFace(userCredentialId, file))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Your KYC verification is not approved yet");
    }

    @Test
    @DisplayName("Should establish baseline photo and succeed face verification if partner has no KYC profile photo stored yet")
    void testVerifyFace_EstablishesBaselineWhenNoKycPhoto() {
        partner.verifyKyc(); // VERIFIED but profileImageKey is null

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));

        MockMultipartFile file = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", new byte[]{ (byte)0xFF, (byte)0xD8, (byte)0xFF });

        boolean result = deliveryService.verifyFace(userCredentialId, file);
        assertThat(result).isTrue();
        assertThat(partner.getProfileImageKey()).isNotNull();
    }

    @Test
    @DisplayName("Should reject face verification when face biometrics matching fails")
    void testVerifyFace_ThrowsWhenBiometricsFail() {
        partner.verifyKyc();
        partner.setProfileImageKey("delivery-partners/1/profile/approved-kyc.jpg");

        byte[] kycBytes = new byte[]{10, 20, 30};
        byte[] liveBytes = new byte[]{40, 50, 60};

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(objectStorageClient.getObject("delivery-partners/1/profile/approved-kyc.jpg"))
                .thenReturn(kycBytes);
        when(faceBiometricsService.verifyFace(any(), any()))
                .thenReturn(new FaceVerificationResult(false, 0.42, "Face verification failed. The scanned face does not match your KYC-approved profile photo."));

        MockMultipartFile file = new MockMultipartFile("file", "live.jpg", "image/jpeg", liveBytes);

        assertThatThrownBy(() -> deliveryService.verifyFace(userCredentialId, file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Face verification failed");
    }

    @Test
    @DisplayName("Should succeed face verification when live face matches verified KYC photo")
    void testVerifyFace_SuccessWhenMatchesKycPhoto() {
        partner.verifyKyc();
        partner.setProfileImageKey("delivery-partners/1/profile/approved-kyc.jpg");

        byte[] kycBytes = new byte[]{10, 20, 30};
        byte[] liveBytes = new byte[]{40, 50, 60};

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(objectStorageClient.getObject("delivery-partners/1/profile/approved-kyc.jpg"))
                .thenReturn(kycBytes);
        when(faceBiometricsService.verifyFace(any(), any()))
                .thenReturn(new FaceVerificationResult(true, 0.88, "Face identity successfully verified against KYC photo."));

        MockMultipartFile file = new MockMultipartFile("file", "live.jpg", "image/jpeg", liveBytes);

        boolean result = deliveryService.verifyFace(userCredentialId, file);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should forbid going online when KYC is not verified")
    void testSetAvailability_ForbidOnlineWhenNotVerified() {
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));

        SetAvailabilityRequestDto request = new SetAvailabilityRequestDto(true);

        assertThatThrownBy(() -> deliveryService.setAvailability(userCredentialId, request))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("KYC must be verified");
    }

    @Test
    @DisplayName("Should allow going online when KYC is verified and profile photo exists")
    void testSetAvailability_AllowOnlineWhenVerified() {
        partner.verifyKyc();
        partner.setProfileImageKey("delivery-partners/1/profile/photo.jpg");

        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId))
                .thenReturn(Optional.of(partner));
        when(deliveryPartnerRepository.save(any(DeliveryPartner.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SetAvailabilityRequestDto request = new SetAvailabilityRequestDto(true);

        var result = deliveryService.setAvailability(userCredentialId, request);

        assertThat(result.isOnline()).isTrue();
        assertThat(partner.isOnline()).isTrue();
    }
}
