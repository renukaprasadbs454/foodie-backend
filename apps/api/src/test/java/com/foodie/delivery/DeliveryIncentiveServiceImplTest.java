package com.foodie.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock private com.foodie.wallet.repository.WalletAccountRepository walletAccountRepository;
    @Mock private com.foodie.wallet.repository.LedgerEntryRepository ledgerEntryRepository;
    @Mock private com.foodie.wallet.repository.PayoutRepository payoutRepository;
    @Mock private com.foodie.delivery.repository.DeliveryShiftBookingRepository shiftBookingRepository;
    @Mock private com.foodie.delivery.repository.DeliveryOnlineSessionRepository onlineSessionRepository;

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
                objectMapper,
                walletAccountRepository,
                ledgerEntryRepository,
                payoutRepository,
                shiftBookingRepository,
                onlineSessionRepository
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
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(any())).thenReturn(List.of());
        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(any())).thenReturn(List.of());
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

    @Test
    void getIncentivesProgress_hidesOffersWhenToggledOffInAdminConfig() {
        LocalDate today = LocalDate.now(ZONE_IST);

        // Turn OFF peakHourBonus and rainBonus, keep others ON
        String jsonConfig = """
        {
          "pricingBasis": "UNIVERSAL",
          "universalConfig": {
            "minPrice": 200,
            "moneyPerKm": 25,
            "incentives": [
              { "id": "basePay", "title": "Base Pay per Order", "value": 50, "unit": "₹ / order", "active": true, "description": "Standard base pay", "category": "Base & Distance" },
              { "id": "peakHourBonus", "title": "Peak Hour Bonus", "value": 100, "unit": "₹ / order", "active": false, "description": "Peak hour surge", "category": "Weather & Surge" },
              { "id": "dailyTargetBonus", "title": "Daily Target Bonus", "value": 150, "unit": "₹ / day", "active": true, "description": "15 orders target", "category": "Target & Mileage" },
              { "id": "weeklyTargetBonus", "title": "Weekly Target Bonus", "value": 800, "unit": "₹ / week", "active": true, "description": "80 orders target", "category": "Target & Mileage" },
              { "id": "longDistanceBonus", "title": "Long-Distance Bonus", "value": 15, "unit": "₹ / extra km", "active": true, "description": "Over 5 km", "category": "Base & Distance" },
              { "id": "rainBonus", "title": "Rain/Bad Weather Bonus", "value": 70, "unit": "₹ / order", "active": false, "description": "Rain surge", "category": "Weather & Surge" },
              { "id": "referralBonus", "title": "Referral Bonus", "value": 500, "unit": "₹ / referral", "active": true, "description": "Referral reward", "category": "Reward & Rating" },
              { "id": "performanceBonus", "title": "Performance/Rating Bonus", "value": 250, "unit": "₹ / week", "active": true, "description": "Weekly rating 4.85+", "category": "Reward & Rating" }
            ]
          }
        }
        """;

        DeliveryPricingConfig configWithToggles = new DeliveryPricingConfig(
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                new BigDecimal("200.00"),
                new BigDecimal("25.00"),
                "UNIVERSAL",
                jsonConfig,
                Instant.now(),
                Instant.now(),
                null
        );
        when(pricingConfigRepository.findById(any())).thenReturn(Optional.of(configWithToggles));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);

        // Active should be 6, and peakHourBonus & rainBonus must NOT be present
        assertThat(progress.offers()).hasSize(6);
        assertThat(progress.offers().stream().map(o -> o.id()).toList())
                .containsExactlyInAnyOrder("basePay", "dailyTargetBonus", "weeklyTargetBonus", "longDistanceBonus", "referralBonus", "performanceBonus");
        assertThat(progress.offers().stream().anyMatch(o -> "peakHourBonus".equals(o.id()))).isFalse();
        assertThat(progress.offers().stream().anyMatch(o -> "rainBonus".equals(o.id()))).isFalse();
    }

    @Test
    void getIncentivesProgress_testEverySingleIncentiveToggleIndividually() {
        LocalDate today = LocalDate.now(ZONE_IST);
        List<String> allEightIds = List.of(
                "basePay", "peakHourBonus", "dailyTargetBonus", "weeklyTargetBonus",
                "longDistanceBonus", "rainBonus", "referralBonus", "performanceBonus"
        );

        for (String targetOffId : allEightIds) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"pricingBasis\":\"UNIVERSAL\",\"universalConfig\":{\"minPrice\":200,\"moneyPerKm\":25,\"incentives\":[");
            for (int i = 0; i < allEightIds.size(); i++) {
                String id = allEightIds.get(i);
                boolean isActive = !id.equals(targetOffId);
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"id\":\"%s\",\"title\":\"Test %s\",\"value\":100,\"unit\":\"₹ / order\",\"active\":%b,\"description\":\"Desc %s\",\"category\":\"Base & Distance\"}", id, id, isActive, id));
            }
            sb.append("]}}");

            DeliveryPricingConfig customConfig = new DeliveryPricingConfig(
                    UUID.fromString("99999999-9999-9999-9999-999999999999"),
                    new BigDecimal("200.00"),
                    new BigDecimal("25.00"),
                    "UNIVERSAL",
                    sb.toString(),
                    Instant.now(),
                    Instant.now(),
                    null
            );
            when(pricingConfigRepository.findById(any())).thenReturn(Optional.of(customConfig));

            IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);

            assertThat(progress.offers()).hasSize(7);
            assertThat(progress.offers().stream().anyMatch(o -> targetOffId.equals(o.id()))).isFalse();

            // All other 7 must be visible with correct details
            for (String otherId : allEightIds) {
                if (!otherId.equals(targetOffId)) {
                    var offer = progress.offers().stream().filter(o -> otherId.equals(o.id())).findFirst().orElse(null);
                    assertThat(offer).isNotNull();
                    assertThat(offer.title()).isEqualTo("Test " + otherId);
                    assertThat(offer.rewardAmount()).isEqualByComparingTo("100.00");
                }
            }
        }
    }

    @Test
    void getIncentivesProgress_allOffersHiddenWhenAllToggledOff() {
        LocalDate today = LocalDate.now(ZONE_IST);

        String jsonConfig = """
        {
          "pricingBasis": "UNIVERSAL",
          "universalConfig": {
            "minPrice": 200,
            "moneyPerKm": 25,
            "incentives": [
              { "id": "basePay", "active": false },
              { "id": "peakHourBonus", "active": false },
              { "id": "dailyTargetBonus", "active": false },
              { "id": "weeklyTargetBonus", "active": false },
              { "id": "longDistanceBonus", "active": false },
              { "id": "rainBonus", "active": false },
              { "id": "referralBonus", "active": false },
              { "id": "performanceBonus", "active": false }
            ]
          }
        }
        """;

        DeliveryPricingConfig configAllOff = new DeliveryPricingConfig(
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                new BigDecimal("200.00"),
                new BigDecimal("25.00"),
                "UNIVERSAL",
                jsonConfig,
                Instant.now(),
                Instant.now(),
                null
        );
        when(pricingConfigRepository.findById(any())).thenReturn(Optional.of(configAllOff));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        assertThat(progress.offers()).isEmpty();
    }

    @Test
    void getIncentivesProgress_calculatesMilestoneLadderCorrectly() {
        LocalDate today = LocalDate.now(ZONE_IST);

        // 7 trips completed today
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(7L);

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst()
                .orElse(null);

        assertThat(dailyOffer).isNotNull();
        assertThat(dailyOffer.milestones()).isNotNull();
        assertThat(dailyOffer.milestones()).hasSize(4);

        // Milestone 1: 6 trips -> completed
        var m1 = dailyOffer.milestones().get(0);
        assertThat(m1.trips()).isEqualTo(6);
        assertThat(m1.rewardAmount()).isEqualByComparingTo("50.00");
        assertThat(m1.status()).isEqualTo("Completed");
        assertThat(m1.remaining()).isEqualTo(0);
        assertThat(m1.isEarned()).isTrue();

        // Milestone 2: 9 trips -> in progress (2 remaining)
        var m2 = dailyOffer.milestones().get(1);
        assertThat(m2.trips()).isEqualTo(9);
        assertThat(m2.rewardAmount()).isEqualByComparingTo("75.00");
        assertThat(m2.status()).isEqualTo("In Progress");
        assertThat(m2.remaining()).isEqualTo(2);
        assertThat(m2.isEarned()).isFalse();

        // Milestone 3: 13 trips -> locked (6 remaining)
        var m3 = dailyOffer.milestones().get(2);
        assertThat(m3.trips()).isEqualTo(13);
        assertThat(m3.rewardAmount()).isEqualByComparingTo("120.00");
        assertThat(m3.status()).isEqualTo("Locked");
        assertThat(m3.remaining()).isEqualTo(6);

        // Milestone 4: 18 trips -> locked (11 remaining)
        var m4 = dailyOffer.milestones().get(3);
        assertThat(m4.trips()).isEqualTo(18);
        assertThat(m4.rewardAmount()).isEqualByComparingTo("190.00");
        assertThat(m4.status()).isEqualTo("Locked");
        assertThat(m4.remaining()).isEqualTo(11);
    }

    @Test
    void getEarningsSummary_returnsSeparatedRegularAndIncentiveEarnings() {
        UUID walletId = UUID.randomUUID();
        var walletAccount = com.foodie.wallet.entity.WalletAccount.open(OwnerType.DELIVERY_PARTNER, partnerId);
        try {
            var idField = com.foodie.common.entity.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(walletAccount, walletId);
        } catch (Exception ignored) {}
        walletAccount.applyCredit(new BigDecimal("1250.00"));

        when(walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partnerId))
                .thenReturn(Optional.of(walletAccount));
        when(ledgerEntryRepository.sumDeliveryCreditsByWalletAccountId(walletId))
                .thenReturn(new BigDecimal("1000.00"));
        when(ledgerEntryRepository.sumIncentiveCreditsByWalletAccountId(walletId))
                .thenReturn(new BigDecimal("250.00"));
        when(earningRepository.sumAmountByDeliveryPartnerId(partnerId))
                .thenReturn(new BigDecimal("250.00"));
        when(payoutRepository.sumAmountByWalletAccountIdAndStatusIn(eq(walletId), any()))
                .thenReturn(new BigDecimal("500.00"));

        var summary = incentiveService.getEarningsSummary(userCredentialId);

        assertThat(summary.regularDeliveryEarnings()).isEqualByComparingTo("1000.00");
        assertThat(summary.incentiveEarnings()).isEqualByComparingTo("250.00");
        assertThat(summary.earnedIncentives()).isEqualByComparingTo("250.00");
        assertThat(summary.paidOutIncentives()).isEqualByComparingTo("500.00");
        assertThat(summary.availableBalance()).isEqualByComparingTo("1250.00");
    }

    @Test
    void getIncentivesProgress_evaluatesDynamicOfferConditionsCorrectly() {
        LocalDate today = LocalDate.now(ZONE_IST);

        // Partner has 0 rejected orders
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndAssignedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.REJECTED), any(), any()))
                .thenReturn(0L);

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst()
                .orElse(null);

        assertThat(dailyOffer).isNotNull();
        assertThat(dailyOffer.conditions()).isNotNull();
        assertThat(dailyOffer.conditions()).hasSize(2);

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(loginCond).isNotNull();
        assertThat(loginCond.title()).contains("Mandatory Login");
        assertThat(loginCond.value()).isEqualTo("7pm - 12am");

        var rejCond = dailyOffer.conditions().stream()
                .filter(c -> "ORDER_REJECTION".equals(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(rejCond).isNotNull();
        assertThat(rejCond.title()).isEqualTo("Other condition");
        assertThat(rejCond.status()).isEqualTo("SATISFIED");
        assertThat(rejCond.currentProgressText()).isEqualTo("0 orders rejected");
    }

    @Test
    void evaluateDailyTarget_orderRejectionExceedingLimitBlocksReward() {
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // Partner completed 18 deliveries (eligible for all milestones and target)
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(18L);

        // BUT partner rejected 2 orders today (limit is 1)
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndAssignedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.REJECTED), any(), any()))
                .thenReturn(2L);

        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(false);

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Verify that walletService.credit was NEVER called because condition failed!
        verify(walletService, never()).credit(
                eq(OwnerType.DELIVERY_PARTNER),
                eq(partnerId),
                any(),
                eq(LedgerReferenceType.INCENTIVE),
                any()
        );
    }

    @Test
    void getIncentivesProgress_orderRejectionConditionFailedLocksMilestones() {
        LocalDate today = LocalDate.now(ZONE_IST);

        // Partner completed 10 deliveries (normally milestone 1 and 2 would be unlocked)
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(10L);

        // But partner has 2 rejected orders (limit: 1) -> condition FAILED
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndAssignedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.REJECTED), any(), any()))
                .thenReturn(2L);

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst()
                .orElse(null);

        assertThat(dailyOffer).isNotNull();
        var rejCond = dailyOffer.conditions().stream()
                .filter(c -> "ORDER_REJECTION".equals(c.type()))
                .findFirst()
                .orElse(null);
        assertThat(rejCond).isNotNull();
        assertThat(rejCond.status()).isEqualTo("FAILED");

        // Milestones must be Locked because condition is FAILED
        for (var m : dailyOffer.milestones()) {
            assertThat(m.status()).isEqualTo("Locked");
            assertThat(m.isEarned()).isFalse();
        }
    }

    @Test
    void getIncentivesProgress_displaysSelectedShiftTimes_7pmTo12am() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        assertThat(loginCond.value()).isEqualTo("7pm - 12am");
        assertThat(loginCond.subtext()).isEqualTo("4h 30m");
        assertThat(loginCond.progressPercentage()).isEqualTo(0.0);
        assertThat(loginCond.status()).isEqualTo("PENDING");
    }

    @Test
    void getIncentivesProgress_displaysDifferentSelectedShift_11amTo4pm() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Lunch Shift", "11:00 AM", "04:00 PM", "5h", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        assertThat(loginCond.value()).isEqualTo("11am - 4pm");
        assertThat(loginCond.subtext()).isEqualTo("5h");
        assertThat(loginCond.progressPercentage()).isEqualTo(0.0);
    }

    @Test
    void getIncentivesProgress_evaluatesDynamicOnlineDurationAndProgress_2h15mOf4h30m() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // 2 hours 15 minutes = 135 minutes online inside 7pm-12am window
        Instant shiftStart = today.atTime(19, 0).atZone(ZONE_IST).toInstant();
        Instant sessionOnline = shiftStart.plus(java.time.Duration.ofMinutes(15));
        Instant sessionOffline = sessionOnline.plus(java.time.Duration.ofMinutes(135));

        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, sessionOnline
        );
        session.setOfflineAt(sessionOffline);
        session.setDurationSeconds(135L * 60L);

        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        assertThat(loginCond.value()).isEqualTo("7pm - 12am");
        assertThat(loginCond.subtext()).isEqualTo("2h 15m");
        assertThat(loginCond.progressPercentage()).isEqualTo(50.0);
        assertThat(loginCond.status()).isEqualTo("PENDING");
    }

    @Test
    void getIncentivesProgress_offlineSessionPausesAccumulation() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // Partner logged in for 60 minutes and went offline
        Instant shiftStart = today.atTime(19, 0).atZone(ZONE_IST).toInstant();
        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, shiftStart
        );
        session.setOfflineAt(shiftStart.plus(java.time.Duration.ofMinutes(60)));
        session.setDurationSeconds(3600L);

        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        // 60 / 270 = ~22.22%
        assertThat(loginCond.subtext()).isEqualTo("1h");
        assertThat(loginCond.progressPercentage()).isCloseTo(22.22, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void getIncentivesProgress_timeOutsideShiftWindowNotCounted() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // Online at 9:00 AM to 11:00 AM (completely outside shift window 7 PM to 12 AM)
        Instant morningOnline = today.atTime(9, 0).atZone(ZONE_IST).toInstant();
        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, morningOnline
        );
        session.setOfflineAt(morningOnline.plus(java.time.Duration.ofHours(2)));

        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        // Must NOT be counted towards shift duration!
        assertThat(loginCond.progressPercentage()).isEqualTo(0.0);
        assertThat(loginCond.subtext()).isEqualTo("4h 30m");
    }

    @Test
    void getIncentivesProgress_completedDurationAchievesSatisfiedStatus() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // Completed 270 minutes (4.5h)
        Instant shiftStart = today.atTime(19, 0).atZone(ZONE_IST).toInstant();
        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, shiftStart
        );
        session.setOfflineAt(shiftStart.plus(java.time.Duration.ofMinutes(270)));

        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        IncentivesProgressResponseDto progress = incentiveService.getIncentivesProgress(userCredentialId, today);
        var dailyOffer = progress.offers().stream()
                .filter(o -> "dailyTargetBonus".equals(o.id()))
                .findFirst().orElseThrow();

        var loginCond = dailyOffer.conditions().stream()
                .filter(c -> "LOGIN_SLOT".equals(c.type()))
                .findFirst().orElseThrow();

        assertThat(loginCond.progressPercentage()).isEqualTo(100.0);
        assertThat(loginCond.status()).isEqualTo("SATISFIED");
        assertThat(loginCond.subtext()).isEqualTo("4h 30m");
    }

    @Test
    void evaluateDailyTarget_whenShiftBooked_unfulfilledSlotBlocksReward() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // Shift booked: 7pm to 12am, required 4.5h (270 mins)
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // Partner completed 15 deliveries (target met)
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(15L);

        // BUT partner only has 60 minutes online (out of 270)
        Instant shiftStart = today.atTime(19, 0).atZone(ZONE_IST).toInstant();
        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, shiftStart
        );
        session.setOfflineAt(shiftStart.plus(java.time.Duration.ofMinutes(60)));
        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(false);

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Daily target reward must NOT be granted because mandatory login slot is unfulfilled!
        verify(walletService, never()).credit(
                eq(OwnerType.DELIVERY_PARTNER),
                eq(partnerId),
                eq(new BigDecimal("150.00")),
                eq(LedgerReferenceType.INCENTIVE),
                any()
        );
    }

    @Test
    void evaluateDailyTarget_whenShiftBooked_fulfilledSlotAwardsReward() {
        LocalDate today = LocalDate.now(ZONE_IST);
        UUID shiftId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        // Shift booked: 7pm to 12am, required 4.5h (270 mins)
        com.foodie.delivery.entity.DeliveryShiftBooking booking = new com.foodie.delivery.entity.DeliveryShiftBooking(
                partnerId, shiftId, "Dinner Shift", "07:00 PM", "12:00 AM", "4.5", "BOOKED", Instant.now()
        );
        when(shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partnerId))
                .thenReturn(List.of(booking));

        // Partner completed 15 deliveries (target met)
        when(deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                eq(partnerId), eq(DeliveryAssignmentStatus.DELIVERED), any(), any()))
                .thenReturn(15L);

        // Partner has 270 minutes online (fully fulfilled)
        Instant shiftStart = today.atTime(19, 0).atZone(ZONE_IST).toInstant();
        com.foodie.delivery.entity.DeliveryOnlineSession session = new com.foodie.delivery.entity.DeliveryOnlineSession(
                partnerId, shiftId, shiftStart
        );
        session.setOfflineAt(shiftStart.plus(java.time.Duration.ofMinutes(270)));
        when(onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partnerId))
                .thenReturn(List.of(session));

        when(earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                eq(partnerId), any(), any(), any())).thenReturn(false);
        when(earningRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        incentiveService.processDeliveryCompletion(partnerId, orderId, assignmentId);

        // Daily target reward ₹150 IS granted!
        verify(walletService).credit(
                eq(OwnerType.DELIVERY_PARTNER),
                eq(partnerId),
                eq(new BigDecimal("150.00")),
                eq(LedgerReferenceType.INCENTIVE),
                any()
        );
    }

    @Test
    void helperMethods_formatTimeAndDurationAccurately() {
        assertThat(DeliveryIncentiveServiceImpl.formatTimeSlotDisplay("07:00 PM", "12:00 AM")).isEqualTo("7pm - 12am");
        assertThat(DeliveryIncentiveServiceImpl.formatTimeSlotDisplay("11:00 AM", "04:00 PM")).isEqualTo("11am - 4pm");
        assertThat(DeliveryIncentiveServiceImpl.formatTimeSlotDisplay("19:00", "00:00")).isEqualTo("7pm - 12am");
        assertThat(DeliveryIncentiveServiceImpl.formatTimeSlotDisplay("06:30 PM", "11:30 PM")).isEqualTo("6:30pm - 11:30pm");

        assertThat(DeliveryIncentiveServiceImpl.formatDurationDisplay(270)).isEqualTo("4h 30m");
        assertThat(DeliveryIncentiveServiceImpl.formatDurationDisplay(135)).isEqualTo("2h 15m");
        assertThat(DeliveryIncentiveServiceImpl.formatDurationDisplay(300)).isEqualTo("5h");
        assertThat(DeliveryIncentiveServiceImpl.formatDurationDisplay(45)).isEqualTo("45m");
        assertThat(DeliveryIncentiveServiceImpl.formatDurationDisplay(0)).isEqualTo("0m");

        assertThat(DeliveryIncentiveServiceImpl.parseDurationMinutes("4.5", 270)).isEqualTo(270);
        assertThat(DeliveryIncentiveServiceImpl.parseDurationMinutes("4h 30m", 270)).isEqualTo(270);
        assertThat(DeliveryIncentiveServiceImpl.parseDurationMinutes("5", 270)).isEqualTo(300);
        assertThat(DeliveryIncentiveServiceImpl.parseDurationMinutes("5h", 270)).isEqualTo(300);
    }
}
