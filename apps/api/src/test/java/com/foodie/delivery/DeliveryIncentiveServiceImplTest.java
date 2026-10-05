package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerIncentiveEarning;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerIncentiveEarningRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.delivery.service.impl.DeliveryIncentiveServiceImpl;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryIncentiveServiceImplTest {

    private static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");

    @Mock private DeliveryPartnerRepository deliveryPartnerRepository;
    @Mock private DeliveryAssignmentRepository deliveryAssignmentRepository;
    @Mock private DeliveryPricingConfigRepository pricingConfigRepository;
    @Mock private DeliveryPartnerIncentiveEarningRepository earningRepository;
    @Mock private WalletService walletService;

    private ObjectMapper objectMapper;
    private DeliveryIncentiveServiceImpl incentiveService;

    private UUID partnerId;
    private UUID userCredentialId;
    private DeliveryPartner partner;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        incentiveService = new DeliveryIncentiveServiceImpl(
                deliveryPartnerRepository,
                deliveryAssignmentRepository,
                pricingConfigRepository,
                earningRepository,
                walletService,
                objectMapper
        );

        partnerId = UUID.randomUUID();
        userCredentialId = UUID.randomUUID();
        partner = DeliveryPartner.create(userCredentialId, "Test Partner", com.foodie.common.enums.VehicleType.BIKE, "KA-01-AB-1234");
        try {
            var idField = com.foodie.common.entity.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(partner, partnerId);
        } catch (Exception ignored) {}

        when(deliveryPartnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(deliveryPartnerRepository.findByUserCredentialId(userCredentialId)).thenReturn(Optional.of(partner));
        when(pricingConfigRepository.findById(any())).thenReturn(Optional.of(DeliveryPricingConfig.createDefault()));
    }

    @Test
    void processDeliveryCompletion_awardsDailyTargetWhenTargetReached() {
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // 15 deliveries completed today
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(15L);

        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(false);

        when(earningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Daily target bonus ₹150 awarded
        verify(walletService).credit(
                eq(OwnerType.DELIVERY_PARTNER),
                eq(partnerId),
                eq(new BigDecimal("150.00")),
                eq(LedgerReferenceType.INCENTIVE),
                any()
        );
    }

    @Test
    void processDeliveryCompletion_doesNotAwardDailyTargetWhenBelowTarget() {
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // 14 deliveries completed today
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(14L);

        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(false);

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Daily target ₹150 is NOT awarded
        verify(walletService, never()).credit(
                eq(OwnerType.DELIVERY_PARTNER),
                eq(partnerId),
                eq(new BigDecimal("150.00")),
                eq(LedgerReferenceType.INCENTIVE),
                any()
        );
    }

    @Test
    void processDeliveryCompletion_avoidsDoubleCounting() {
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // Already exists in repo
        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(true);

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Zero wallet credits because all were already awarded
        verify(walletService, never()).credit(any(), any(), any(), any(), any());
    }

    @Test
    void getIncentivesProgress_returnsLiveProgressAndOffers() {
        LocalDate today = LocalDate.now(ZONE_IST);

        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(8L);

        DeliveryPartnerIncentiveEarning earned = DeliveryPartnerIncentiveEarning.create(
                partner, "basePay", "Base Pay per Order", new BigDecimal("50.00"), "DELIVERY_ASSIGNMENT", UUID.randomUUID(), null, today
        );
        when(earningRepository.findByDeliveryPartnerIdAndPeriodDate(partnerId, today))
                .thenReturn(List.of(earned));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);

        assertThat(progress.tripsCompleted()).isEqualTo(8L);
        assertThat(progress.incentivesEarned()).isEqualByComparingTo("50.00");
        assertThat(progress.offers()).isNotEmpty();

        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst()
                .orElse(null);

        assertThat(dailyOffer).isNotNull();
        assertThat(dailyOffer.target()).isEqualTo(15);
        assertThat(dailyOffer.currentProgress()).isEqualTo(8);
        assertThat(dailyOffer.remaining()).isEqualTo(7);
        assertThat(dailyOffer.status()).isEqualTo("In Progress");
    }
}
