package com.foodie.delivery.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.delivery.dto.response.DeliveryIncentivesEarningsSummaryDto;
import com.foodie.delivery.dto.response.IncentiveEarningHistoryDto;
import com.foodie.delivery.dto.response.IncentiveMilestoneDto;
import com.foodie.delivery.dto.response.IncentiveOfferProgressDto;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerIncentiveEarning;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryPartnerIncentiveEarningRepository;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.delivery.service.DeliveryIncentiveService;
import com.foodie.wallet.repository.LedgerEntryRepository;
import com.foodie.wallet.repository.PayoutRepository;
import com.foodie.wallet.repository.WalletAccountRepository;
import com.foodie.wallet.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryIncentiveServiceImpl implements DeliveryIncentiveService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryIncentiveServiceImpl.class);
    private static final UUID DEFAULT_CONFIG_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");
    private static final Pattern TARGET_PATTERN = Pattern.compile("(\\d+)\\s*(?:or more\\s*)?(?:orders|deliveries|trips)", Pattern.CASE_INSENSITIVE);

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final DeliveryPricingConfigRepository pricingConfigRepository;
    private final DeliveryPartnerIncentiveEarningRepository earningRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private WalletAccountRepository walletAccountRepository;

    @Autowired(required = false)
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired(required = false)
    private PayoutRepository payoutRepository;

    public DeliveryIncentiveServiceImpl(
            DeliveryPartnerRepository deliveryPartnerRepository,
            DeliveryAssignmentRepository deliveryAssignmentRepository,
            DeliveryPricingConfigRepository pricingConfigRepository,
            DeliveryPartnerIncentiveEarningRepository earningRepository,
            WalletService walletService,
            ObjectMapper objectMapper
    ) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.deliveryAssignmentRepository = deliveryAssignmentRepository;
        this.pricingConfigRepository = pricingConfigRepository;
        this.earningRepository = earningRepository;
        this.walletService = walletService;
        this.objectMapper = objectMapper;
    }

    public DeliveryIncentiveServiceImpl(
            DeliveryPartnerRepository deliveryPartnerRepository,
            DeliveryAssignmentRepository deliveryAssignmentRepository,
            DeliveryPricingConfigRepository pricingConfigRepository,
            DeliveryPartnerIncentiveEarningRepository earningRepository,
            WalletService walletService,
            ObjectMapper objectMapper,
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PayoutRepository payoutRepository
    ) {
        this(deliveryPartnerRepository, deliveryAssignmentRepository, pricingConfigRepository, earningRepository, walletService, objectMapper);
        this.walletAccountRepository = walletAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.payoutRepository = payoutRepository;
    }

    public record MilestoneDef(
            int trips,
            BigDecimal reward
    ) {}

    public record IncentiveRuleDef(
            String id,
            String title,
            BigDecimal value,
            String unit,
            boolean active,
            String description,
            String category,
            String condition,
            Integer targetCount,
            List<MilestoneDef> milestones
    ) {
        public IncentiveRuleDef(
                String id,
                String title,
                BigDecimal value,
                String unit,
                boolean active,
                String description,
                String category,
                String condition
        ) {
            this(id, title, value, unit, active, description, category, condition, null, null);
        }
    }

    @Override
    @Transactional
    public void processDeliveryCompletion(UUID deliveryPartnerId, UUID orderId, UUID assignmentId) {
        log.info("Evaluating incentives for partner {} assignment {} order {}", deliveryPartnerId, assignmentId, orderId);

        DeliveryPartner partner = deliveryPartnerRepository.findById(deliveryPartnerId)
                .orElse(null);
        if (partner == null) {
            log.warn("Partner not found for incentive evaluation: {}", deliveryPartnerId);
            return;
        }

        DeliveryAssignment assignment = deliveryAssignmentRepository.findById(assignmentId)
                .orElse(null);
        Instant completedAt = (assignment != null && assignment.getDeliveredVerifiedAt() != null)
                ? assignment.getDeliveredVerifiedAt()
                : Instant.now();

        ZonedDateTime zdt = completedAt.atZone(ZONE_IST);
        LocalDate periodDate = zdt.toLocalDate();

        List<IncentiveRuleDef> activeRules = loadActiveRules();

        for (IncentiveRuleDef rule : activeRules) {
            if (!rule.active()) {
                continue;
            }

            try {
                switch (rule.id()) {
                    case "basePay" -> evaluateBasePay(partner, rule, assignmentId, orderId, periodDate);
                    case "peakHourBonus" -> evaluatePeakHour(partner, rule, assignmentId, orderId, periodDate, zdt.toLocalTime());
                    case "dailyTargetBonus" -> evaluateDailyTarget(partner, rule, periodDate);
                    case "weeklyTargetBonus" -> evaluateWeeklyTarget(partner, rule, periodDate);
                    case "longDistanceBonus" -> evaluateLongDistance(partner, rule, assignmentId, orderId, periodDate);
                    case "rainBonus" -> evaluateRainBonus(partner, rule, assignmentId, orderId, periodDate);
                    case "referralBonus" -> {
                        // Referral bonus handled when referral conditions are met
                    }
                    case "performanceBonus" -> evaluatePerformanceBonus(partner, rule, periodDate);
                    default -> {
                        // Custom rules default to per-order surge if active
                        evaluateCustomRule(partner, rule, assignmentId, orderId, periodDate);
                    }
                }
            } catch (Exception e) {
                log.error("Error evaluating incentive rule {} for partner {}: {}", rule.id(), partner.getId(), e.getMessage(), e);
            }
        }
    }

    private void evaluateBasePay(DeliveryPartner partner, IncentiveRuleDef rule, UUID assignmentId, UUID orderId, LocalDate periodDate) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
    }

    private void evaluatePeakHour(
            DeliveryPartner partner,
            IncentiveRuleDef rule,
            UUID assignmentId,
            UUID orderId,
            LocalDate periodDate,
            LocalTime completionTime
    ) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        if (isPeakHour(completionTime)) {
            awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
        }
    }

    private void evaluateLongDistance(
            DeliveryPartner partner,
            IncentiveRuleDef rule,
            UUID assignmentId,
            UUID orderId,
            LocalDate periodDate
    ) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        double distanceKm = 8.0;
        if (distanceKm > 5.0) {
            double extraKm = distanceKm - 5.0;
            BigDecimal extraAmount = rule.value().multiply(BigDecimal.valueOf(extraKm)).setScale(2, RoundingMode.HALF_UP);
            awardIncentive(partner, rule.id(), rule.title(), extraAmount, "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
        }
    }

    private void evaluateRainBonus(DeliveryPartner partner, IncentiveRuleDef rule, UUID assignmentId, UUID orderId, LocalDate periodDate) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        if (isBadWeatherConditionActive(assignmentId, orderId)) {
            awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
        }
    }

    private boolean isBadWeatherConditionActive(UUID assignmentId, UUID orderId) {
        String weatherEnv = System.getProperty("foodie.weather.condition", System.getenv("FOODIE_WEATHER_CONDITION"));
        if ("BAD_WEATHER".equalsIgnoreCase(weatherEnv) || "RAIN".equalsIgnoreCase(weatherEnv)) {
            return true;
        }
        try {
            DeliveryPricingConfig config = pricingConfigRepository.findById(DEFAULT_CONFIG_ID).orElse(null);
            if (config != null && config.getConfigData() != null) {
                if (config.getConfigData().contains("\"condition\":\"BAD_WEATHER\"") || config.getConfigData().contains("\"condition\": \"BAD_WEATHER\"")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void evaluateCustomRule(DeliveryPartner partner, IncentiveRuleDef rule, UUID assignmentId, UUID orderId, LocalDate periodDate) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
    }

    private void evaluateDailyTarget(DeliveryPartner partner, IncentiveRuleDef rule, LocalDate today) {
        ZonedDateTime startOfDay = today.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfDay = today.atTime(LocalTime.MAX).atZone(ZONE_IST);

        long completedToday = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.DELIVERED,
                startOfDay.toInstant(),
                endOfDay.toInstant()
        );

        // Evaluate milestone ladder if configured
        if (rule.milestones() != null && !rule.milestones().isEmpty()) {
            for (MilestoneDef milestone : rule.milestones()) {
                if (completedToday >= milestone.trips() && milestone.reward().compareTo(BigDecimal.ZERO) > 0) {
                    UUID milestoneRefId = UUID.nameUUIDFromBytes(
                            (partner.getId() + ":DAILY_TARGET_MILESTONE:" + today + ":" + milestone.trips()).getBytes(StandardCharsets.UTF_8)
                    );
                    awardIncentive(
                            partner,
                            rule.id() + "_m" + milestone.trips(),
                            rule.title() + " (" + milestone.trips() + " trips milestone)",
                            milestone.reward(),
                            "DAILY_TARGET_MILESTONE",
                            milestoneRefId,
                            null,
                            today
                    );
                }
            }
        }

        // Evaluate main target
        int target = rule.targetCount() != null ? rule.targetCount() : parseTargetFromDescription(rule.description(), 15);
        if (rule.value().compareTo(BigDecimal.ZERO) > 0 && completedToday >= target) {
            UUID dailyRefId = UUID.nameUUIDFromBytes((partner.getId() + ":DAILY_TARGET:" + today).getBytes(StandardCharsets.UTF_8));
            awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DAILY_TARGET", dailyRefId, null, today);
        }
    }

    private void evaluateWeeklyTarget(DeliveryPartner partner, IncentiveRuleDef rule, LocalDate today) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;

        int target = rule.targetCount() != null ? rule.targetCount() : parseTargetFromDescription(rule.description(), 80);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        ZonedDateTime startOfWeek = weekStart.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfWeek = weekEnd.atTime(LocalTime.MAX).atZone(ZONE_IST);

        long completedThisWeek = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.DELIVERED,
                startOfWeek.toInstant(),
                endOfWeek.toInstant()
        );

        if (completedThisWeek >= target) {
            UUID weeklyRefId = UUID.nameUUIDFromBytes((partner.getId() + ":WEEKLY_TARGET:" + weekStart).getBytes(StandardCharsets.UTF_8));
            awardIncentive(partner, rule.id(), rule.title(), rule.value(), "WEEKLY_TARGET", weeklyRefId, null, today);
        }
    }

    private void evaluatePerformanceBonus(DeliveryPartner partner, IncentiveRuleDef rule, LocalDate today) {
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UUID perfRefId = UUID.nameUUIDFromBytes((partner.getId() + ":PERFORMANCE:" + weekStart).getBytes(StandardCharsets.UTF_8));

        awardIncentive(partner, rule.id(), rule.title(), rule.value(), "PERFORMANCE", perfRefId, null, today);
    }

    private void awardIncentive(
            DeliveryPartner partner,
            String ruleId,
            String ruleTitle,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            UUID orderId,
            LocalDate periodDate
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (earningRepository.existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
                partner.getId(), ruleId, referenceType, referenceId)) {
            log.info("Incentive {} already awarded for partner {} ref {}", ruleId, partner.getId(), referenceId);
            return;
        }

        try {
            DeliveryPartnerIncentiveEarning earning = DeliveryPartnerIncentiveEarning.create(
                    partner,
                    ruleId,
                    ruleTitle,
                    amount,
                    referenceType,
                    referenceId,
                    orderId,
                    periodDate
            );
            earning = earningRepository.save(earning);

            log.info("Awarded incentive {} ({}) amount ₹{} to partner {}", ruleId, ruleTitle, amount, partner.getId());

            walletService.credit(
                    OwnerType.DELIVERY_PARTNER,
                    partner.getId(),
                    amount,
                    LedgerReferenceType.INCENTIVE,
                    earning.getId()
            );
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent incentive award prevented for partner {} rule {} ref {}", partner.getId(), ruleId, referenceId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public IncentivesProgressResponseDto getIncentivesProgress(UUID userCredentialId, LocalDate queryDate) {
        DeliveryPartner partner = requirePartner(userCredentialId);
        LocalDate targetDate = (queryDate != null) ? queryDate : LocalDate.now(ZONE_IST);

        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfDay = targetDate.atTime(LocalTime.MAX).atZone(ZONE_IST);

        long tripsCompleted = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.DELIVERED,
                startOfDay.toInstant(),
                endOfDay.toInstant()
        );

        List<DeliveryPartnerIncentiveEarning> dayEarnings = earningRepository
                .findByDeliveryPartnerIdAndPeriodDate(partner.getId(), targetDate);

        BigDecimal totalEarned = dayEarnings.stream()
                .map(DeliveryPartnerIncentiveEarning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<IncentiveRuleDef> activeRules = loadActiveRules();
        List<IncentiveOfferProgressDto> offerDtos = buildOfferProgressList(partner, activeRules, targetDate, tripsCompleted, dayEarnings);

        return new IncentivesProgressResponseDto(
                targetDate,
                tripsCompleted,
                totalEarned,
                offerDtos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncentiveOfferProgressDto> getActiveIncentives(UUID userCredentialId) {
        DeliveryPartner partner = requirePartner(userCredentialId);
        LocalDate today = LocalDate.now(ZONE_IST);

        ZonedDateTime startOfDay = today.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfDay = today.atTime(LocalTime.MAX).atZone(ZONE_IST);

        long tripsToday = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.DELIVERED,
                startOfDay.toInstant(),
                endOfDay.toInstant()
        );

        List<DeliveryPartnerIncentiveEarning> dayEarnings = earningRepository
                .findByDeliveryPartnerIdAndPeriodDate(partner.getId(), today);

        List<IncentiveRuleDef> activeRules = loadActiveRules();
        return buildOfferProgressList(partner, activeRules, today, tripsToday, dayEarnings);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncentiveEarningHistoryDto> getIncentiveEarnings(UUID userCredentialId) {
        DeliveryPartner partner = requirePartner(userCredentialId);
        return earningRepository.findByDeliveryPartnerIdOrderByEarnedAtDesc(partner.getId())
                .stream()
                .map(e -> new IncentiveEarningHistoryDto(
                        e.getId(),
                        e.getRuleId(),
                        e.getRuleTitle(),
                        e.getAmount(),
                        e.getReferenceType(),
                        e.getReferenceId(),
                        e.getOrderId(),
                        e.getPeriodDate(),
                        e.getEarnedAt()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryIncentivesEarningsSummaryDto getEarningsSummary(UUID userCredentialId) {
        DeliveryPartner partner = requirePartner(userCredentialId);

        BigDecimal regularEarnings = BigDecimal.ZERO;
        BigDecimal incentiveEarnings = BigDecimal.ZERO;
        BigDecimal availableBalance = BigDecimal.ZERO;
        BigDecimal paidOutIncentives = BigDecimal.ZERO;

        if (walletAccountRepository != null) {
            var optAcc = walletAccountRepository.findByOwnerTypeAndOwnerId(OwnerType.DELIVERY_PARTNER, partner.getId());
            if (optAcc.isPresent()) {
                var acc = optAcc.get();
                availableBalance = acc.getBalance() != null ? acc.getBalance() : BigDecimal.ZERO;

                if (ledgerEntryRepository != null) {
                    BigDecimal reg = ledgerEntryRepository.sumDeliveryCreditsByWalletAccountId(acc.getId());
                    if (reg != null) regularEarnings = reg;

                    BigDecimal inc = ledgerEntryRepository.sumIncentiveCreditsByWalletAccountId(acc.getId());
                    if (inc != null) incentiveEarnings = inc;
                }

                if (payoutRepository != null) {
                    BigDecimal paid = payoutRepository.sumAmountByWalletAccountIdAndStatusIn(
                            acc.getId(),
                            List.of(PayoutStatus.COMPLETED)
                    );
                    if (paid != null) paidOutIncentives = paid;
                }
            }
        }

        BigDecimal earnedIncentives = earningRepository.sumAmountByDeliveryPartnerId(partner.getId());
        if (earnedIncentives == null || earnedIncentives.compareTo(BigDecimal.ZERO) == 0) {
            earnedIncentives = incentiveEarnings;
        }

        // Calculate pending incentives (potential reward of offers currently In Progress today)
        LocalDate today = LocalDate.now(ZONE_IST);
        IncentivesProgressResponseDto progress = getIncentivesProgress(userCredentialId, today);
        BigDecimal pendingIncentives = BigDecimal.ZERO;
        if (progress != null && progress.offers() != null) {
            for (var offer : progress.offers()) {
                if (offer.milestones() != null && !offer.milestones().isEmpty()) {
                    for (var m : offer.milestones()) {
                        if ("In Progress".equalsIgnoreCase(m.status())) {
                            pendingIncentives = pendingIncentives.add(m.rewardAmount() != null ? m.rewardAmount() : BigDecimal.ZERO);
                        }
                    }
                } else if ("In Progress".equalsIgnoreCase(offer.status()) && !offer.isEarned()) {
                    pendingIncentives = pendingIncentives.add(offer.rewardAmount() != null ? offer.rewardAmount() : BigDecimal.ZERO);
                }
            }
        }

        List<IncentiveEarningHistoryDto> history = getIncentiveEarnings(userCredentialId);

        return new DeliveryIncentivesEarningsSummaryDto(
                regularEarnings.setScale(2, RoundingMode.HALF_UP),
                incentiveEarnings.setScale(2, RoundingMode.HALF_UP),
                earnedIncentives.setScale(2, RoundingMode.HALF_UP),
                pendingIncentives.setScale(2, RoundingMode.HALF_UP),
                paidOutIncentives.setScale(2, RoundingMode.HALF_UP),
                availableBalance.setScale(2, RoundingMode.HALF_UP),
                history
        );
    }

    private List<IncentiveOfferProgressDto> buildOfferProgressList(
            DeliveryPartner partner,
            List<IncentiveRuleDef> rules,
            LocalDate date,
            long tripsToday,
            List<DeliveryPartnerIncentiveEarning> dayEarnings
    ) {
        List<IncentiveOfferProgressDto> list = new ArrayList<>();
        Map<String, BigDecimal> earnedMap = new LinkedHashMap<>();
        for (DeliveryPartnerIncentiveEarning e : dayEarnings) {
            earnedMap.merge(e.getRuleId(), e.getAmount(), BigDecimal::add);
        }

        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long tripsThisWeek = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndDeliveredVerifiedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.DELIVERED,
                weekStart.atStartOfDay(ZONE_IST).toInstant(),
                weekEnd.atTime(LocalTime.MAX).atZone(ZONE_IST).toInstant()
        );

        LocalTime nowTime = LocalTime.now(ZONE_IST);
        boolean isPeak = isPeakHour(nowTime);

        for (IncentiveRuleDef rule : rules) {
            if (!rule.active()) {
                continue;
            }

            int target;
            int currentProgress;
            boolean isEarned;
            String status;
            Integer remaining;
            String validity;
            List<IncentiveMilestoneDto> offerMilestones = null;

            switch (rule.id()) {
                case "basePay" -> {
                    target = 1;
                    isEarned = (tripsToday > 0) || earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : 0;
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : "In Progress";
                    validity = "Per Order Completed";
                }
                case "dailyTargetBonus" -> {
                    target = rule.targetCount() != null ? rule.targetCount() : parseTargetFromDescription(rule.description(), 15);
                    currentProgress = (int) Math.min(tripsToday, target);
                    isEarned = (tripsToday >= target) || earnedMap.containsKey(rule.id());
                    remaining = Math.max(0, target - (int) tripsToday);
                    status = isEarned ? "Completed" : (tripsToday > 0 ? "In Progress" : "Upcoming");
                    validity = "Today (Ends 11:59 PM)";

                    if (rule.milestones() != null && !rule.milestones().isEmpty()) {
                        offerMilestones = new ArrayList<>();
                        boolean firstUnearnedSeen = false;
                        for (MilestoneDef m : rule.milestones()) {
                            boolean mEarned = (tripsToday >= m.trips()) || earnedMap.containsKey(rule.id() + "_m" + m.trips());
                            int mRemaining = Math.max(0, m.trips() - (int) tripsToday);
                            String mStatus;
                            if (mEarned) {
                                mStatus = "Completed";
                            } else if (!firstUnearnedSeen) {
                                mStatus = "In Progress";
                                firstUnearnedSeen = true;
                            } else {
                                mStatus = "Locked";
                            }
                            offerMilestones.add(new IncentiveMilestoneDto(m.trips(), m.reward(), mStatus, mRemaining, mEarned));
                        }
                    }
                }
                case "weeklyTargetBonus" -> {
                    target = rule.targetCount() != null ? rule.targetCount() : parseTargetFromDescription(rule.description(), 80);
                    currentProgress = (int) Math.min(tripsThisWeek, target);
                    isEarned = (tripsThisWeek >= target);
                    remaining = Math.max(0, target - (int) tripsThisWeek);
                    status = isEarned ? "Completed" : (tripsThisWeek > 0 ? "In Progress" : "Upcoming");
                    validity = "This Week (" + weekStart.getDayOfMonth() + "-" + weekEnd.getDayOfMonth() + " " + weekEnd.getMonth().name().substring(0, 3) + ")";
                }
                case "peakHourBonus" -> {
                    target = 1;
                    isEarned = earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : (isPeak ? 1 : 0);
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : (isPeak ? "In Progress" : "Upcoming");
                    validity = "12 PM–3 PM & 7 PM–11 PM";
                }
                case "longDistanceBonus" -> {
                    target = 1;
                    isEarned = earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : 0;
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : "In Progress";
                    validity = "Deliveries > 5 km";
                }
                case "rainBonus" -> {
                    target = 1;
                    isEarned = earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : 0;
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : "In Progress";
                    validity = "Severe Weather Surge";
                }
                case "referralBonus" -> {
                    target = rule.targetCount() != null ? rule.targetCount() : 25;
                    currentProgress = 0;
                    isEarned = earnedMap.containsKey(rule.id());
                    remaining = target;
                    status = isEarned ? "Completed" : "Upcoming";
                    validity = "Per Qualified Referral";
                }
                case "performanceBonus" -> {
                    target = 1;
                    isEarned = earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : 0;
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : "In Progress";
                    validity = "Weekly Rating > 4.85";
                }
                default -> {
                    target = 1;
                    isEarned = earnedMap.containsKey(rule.id());
                    currentProgress = isEarned ? 1 : 0;
                    remaining = isEarned ? 0 : 1;
                    status = isEarned ? "Completed" : "In Progress";
                    validity = "Active Promotion";
                }
            }

            list.add(new IncentiveOfferProgressDto(
                    rule.id(),
                    rule.title(),
                    rule.category(),
                    rule.description(),
                    target,
                    currentProgress,
                    rule.value(),
                    rule.unit(),
                    status,
                    remaining,
                    validity,
                    isEarned,
                    rule.active(),
                    offerMilestones
            ));
        }

        return list;
    }

    private List<IncentiveRuleDef> loadActiveRules() {
        List<IncentiveRuleDef> rules = new ArrayList<>();
        try {
            DeliveryPricingConfig config = pricingConfigRepository.findById(DEFAULT_CONFIG_ID).orElse(null);
            if (config != null && config.getConfigData() != null && !config.getConfigData().isBlank()) {
                JsonNode root = objectMapper.readTree(config.getConfigData());
                JsonNode incentivesNode = null;

                String basis = root.has("pricingBasis") ? root.get("pricingBasis").asText("UNIVERSAL") : "UNIVERSAL";
                if ("ZONE".equalsIgnoreCase(basis) && root.has("zoneConfigs")) {
                    JsonNode zoneConfigs = root.get("zoneConfigs");
                    if (root.has("selectedZoneId") && zoneConfigs.has(root.get("selectedZoneId").asText())) {
                        JsonNode sel = zoneConfigs.get(root.get("selectedZoneId").asText());
                        if (sel.has("incentives")) {
                            incentivesNode = sel.get("incentives");
                        }
                    }
                    if (incentivesNode == null && zoneConfigs.fields().hasNext()) {
                        Map.Entry<String, JsonNode> entry = zoneConfigs.fields().next();
                        if (entry.getValue().has("incentives")) {
                            incentivesNode = entry.getValue().get("incentives");
                        }
                    }
                }

                if (incentivesNode == null && root.has("universalConfig") && root.get("universalConfig").has("incentives")) {
                    incentivesNode = root.get("universalConfig").get("incentives");
                }

                if (incentivesNode == null && root.has("incentives") && root.get("incentives").isArray()) {
                    incentivesNode = root.get("incentives");
                }

                if (incentivesNode != null && incentivesNode.isArray()) {
                    for (JsonNode item : incentivesNode) {
                        String id = item.has("id") ? item.get("id").asText() : "";
                        String title = item.has("title") ? item.get("title").asText() : "";
                        BigDecimal value = item.has("value") ? new BigDecimal(item.get("value").asText("0")) : BigDecimal.ZERO;
                        String unit = item.has("unit") ? item.get("unit").asText("₹ / order") : "₹ / order";
                        boolean active = true;
                        if (item.has("active")) {
                            JsonNode activeNode = item.get("active");
                            active = activeNode.isBoolean() ? activeNode.asBoolean() : !"false".equalsIgnoreCase(activeNode.asText());
                        }
                        String desc = item.has("description") ? item.get("description").asText("") : "";
                        String category = item.has("category") ? item.get("category").asText("Reward & Rating") : "Reward & Rating";
                        String condition = item.has("condition") && !item.get("condition").asText().isBlank()
                                ? item.get("condition").asText()
                                : ("rainBonus".equalsIgnoreCase(id) ? "BAD_WEATHER" : "");

                        Integer targetCount = null;
                        if (item.has("targetCount") && !item.get("targetCount").isNull()) {
                            targetCount = item.get("targetCount").asInt();
                        }

                        List<MilestoneDef> milestones = null;
                        if (item.has("milestones") && item.get("milestones").isArray()) {
                            milestones = new ArrayList<>();
                            for (JsonNode mNode : item.get("milestones")) {
                                if (mNode.has("trips")) {
                                    int mTrips = mNode.get("trips").asInt();
                                    BigDecimal mReward = mNode.has("reward")
                                            ? new BigDecimal(mNode.get("reward").asText("0"))
                                            : (mNode.has("rewardAmount") ? new BigDecimal(mNode.get("rewardAmount").asText("0")) : BigDecimal.ZERO);
                                    milestones.add(new MilestoneDef(mTrips, mReward));
                                }
                            }
                        } else if ("dailyTargetBonus".equalsIgnoreCase(id)) {
                            milestones = List.of(
                                    new MilestoneDef(6, new BigDecimal("50.00")),
                                    new MilestoneDef(9, new BigDecimal("75.00")),
                                    new MilestoneDef(13, new BigDecimal("120.00")),
                                    new MilestoneDef(18, new BigDecimal("190.00"))
                            );
                        }

                        if (!id.isBlank()) {
                            rules.add(new IncentiveRuleDef(id, title, value, unit, active, desc, category, condition, targetCount, milestones));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse incentive rules from configData: {}", e.getMessage());
        }

        if (rules.isEmpty()) {
            rules.addAll(defaultRules());
        }
        return rules;
    }

    private List<IncentiveRuleDef> defaultRules() {
        return List.of(
                new IncentiveRuleDef("basePay", "Base Pay per Order", new BigDecimal("50.00"), "₹ / order", true, "Standard baseline compensation per fulfilled delivery assignment.", "Base & Distance", "", 1, null),
                new IncentiveRuleDef("peakHourBonus", "Peak Hour Bonus", new BigDecimal("100.00"), "₹ / order", true, "Extra surge payout during high-demand meal hours (12 PM–3 PM & 7 PM–11 PM).", "Weather & Surge", "PEAK_HOUR", 1, null),
                new IncentiveRuleDef("dailyTargetBonus", "Daily Target Bonus", new BigDecimal("150.00"), "₹ / day", true, "Bonus awarded upon completing daily order targets.", "Target & Mileage", "DAILY_TARGET", 15,
                        List.of(
                                new MilestoneDef(6, new BigDecimal("50.00")),
                                new MilestoneDef(9, new BigDecimal("75.00")),
                                new MilestoneDef(13, new BigDecimal("120.00")),
                                new MilestoneDef(18, new BigDecimal("190.00"))
                        )
                ),
                new IncentiveRuleDef("weeklyTargetBonus", "Weekly Target Bonus", new BigDecimal("800.00"), "₹ / week", true, "Tier-1 weekly payout bonus for completing 80+ deliveries per week.", "Target & Mileage", "WEEKLY_TARGET", 80, null),
                new IncentiveRuleDef("longDistanceBonus", "Long-Distance Bonus", new BigDecimal("15.00"), "₹ / extra km", true, "Additional mileage incentive for deliveries exceeding 5 km radius.", "Base & Distance", "LONG_DISTANCE", 1, null),
                new IncentiveRuleDef("rainBonus", "Rain/Bad Weather Bonus", new BigDecimal("70.00"), "₹ / order", true, "Weather surge bonus automatically applied during rain or severe weather conditions.", "Weather & Surge", "BAD_WEATHER", 1, null),
                new IncentiveRuleDef("referralBonus", "Referral Bonus", new BigDecimal("500.00"), "₹ / referral", true, "Onboarding reward paid after referred delivery partner completes 25 orders.", "Reward & Rating", "REFERRAL", 25, null),
                new IncentiveRuleDef("performanceBonus", "Performance/Rating Bonus", new BigDecimal("250.00"), "₹ / week", true, "Weekly quality incentive for maintaining customer rating of 4.85+ stars.", "Reward & Rating", "PERFORMANCE", 1, null)
        );
    }

    private boolean isPeakHour(LocalTime time) {
        int hour = time.getHour();
        return (hour >= 12 && hour < 15) || (hour >= 19 && hour < 23);
    }

    private int parseTargetFromDescription(String description, int defaultValue) {
        if (description == null || description.isBlank()) return defaultValue;
        Matcher matcher = TARGET_PATTERN.matcher(description);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private DeliveryPartner requirePartner(UUID userCredentialId) {
        return deliveryPartnerRepository.findByUserCredentialId(userCredentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner profile not found."));
    }
}
