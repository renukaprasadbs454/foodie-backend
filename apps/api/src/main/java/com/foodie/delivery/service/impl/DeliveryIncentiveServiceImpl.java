package com.foodie.delivery.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.enums.DeliveryAssignmentStatus;
import com.foodie.common.enums.LedgerReferenceType;
import com.foodie.common.enums.OwnerType;
import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.delivery.dto.response.DeliveryIncentivesEarningsSummaryDto;
import com.foodie.delivery.dto.response.IncentiveConditionProgressDto;
import com.foodie.delivery.dto.response.IncentiveEarningHistoryDto;
import com.foodie.delivery.dto.response.IncentiveMilestoneDto;
import com.foodie.delivery.dto.response.IncentiveOfferProgressDto;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerIncentiveEarning;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.entity.DeliveryOnlineSession;
import com.foodie.delivery.entity.DeliveryShiftBooking;
import com.foodie.delivery.repository.DeliveryAssignmentRepository;
import com.foodie.delivery.repository.DeliveryOnlineSessionRepository;
import com.foodie.delivery.repository.DeliveryPartnerIncentiveEarningRepository;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.security.AdminAccess;
import com.foodie.delivery.repository.DeliveryPartnerRepository;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.delivery.repository.DeliveryShiftBookingRepository;
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

    @Autowired(required = false)
    private DeliveryShiftBookingRepository shiftBookingRepository;

    @Autowired(required = false)
    private DeliveryOnlineSessionRepository onlineSessionRepository;

    @Autowired(required = false)
    private LocationZoneRepository locationZoneRepository;

    @Autowired
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

    public DeliveryIncentiveServiceImpl(
            DeliveryPartnerRepository deliveryPartnerRepository,
            DeliveryAssignmentRepository deliveryAssignmentRepository,
            DeliveryPricingConfigRepository pricingConfigRepository,
            DeliveryPartnerIncentiveEarningRepository earningRepository,
            WalletService walletService,
            ObjectMapper objectMapper,
            WalletAccountRepository walletAccountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PayoutRepository payoutRepository,
            DeliveryShiftBookingRepository shiftBookingRepository,
            DeliveryOnlineSessionRepository onlineSessionRepository
    ) {
        this(deliveryPartnerRepository, deliveryAssignmentRepository, pricingConfigRepository, earningRepository, walletService, objectMapper, walletAccountRepository, ledgerEntryRepository, payoutRepository);
        this.shiftBookingRepository = shiftBookingRepository;
        this.onlineSessionRepository = onlineSessionRepository;
    }

    public void setShiftBookingRepository(DeliveryShiftBookingRepository shiftBookingRepository) {
        this.shiftBookingRepository = shiftBookingRepository;
    }

    public void setOnlineSessionRepository(DeliveryOnlineSessionRepository onlineSessionRepository) {
        this.onlineSessionRepository = onlineSessionRepository;
    }

    public void setLocationZoneRepository(LocationZoneRepository locationZoneRepository) {
        this.locationZoneRepository = locationZoneRepository;
    }

    public record MilestoneDef(
            int trips,
            BigDecimal reward
    ) {}

    public record ConditionDef(
            String id,
            String type,
            String title,
            String value,
            String subtext,
            String timeSlot,
            String duration,
            Integer minDurationMinutes,
            Integer maxRejections
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
            List<MilestoneDef> milestones,
            List<ConditionDef> conditions,
            String zoneId,
            String creatorRole,
            String scope
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
            this(id, title, value, unit, active, description, category, condition, null, null, null, null, null, null);
        }

        public IncentiveRuleDef(
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
            this(id, title, value, unit, active, description, category, condition, targetCount, milestones, null, null, null, null);
        }

        public IncentiveRuleDef(
                String id,
                String title,
                BigDecimal value,
                String unit,
                boolean active,
                String description,
                String category,
                String condition,
                Integer targetCount,
                List<MilestoneDef> milestones,
                List<ConditionDef> conditions
        ) {
            this(id, title, value, unit, active, description, category, condition, targetCount, milestones, conditions, null, null, null);
        }

        public IncentiveRuleDef(
                String id,
                String title,
                BigDecimal value,
                String unit,
                boolean active,
                String description,
                String category,
                String condition,
                Integer targetCount,
                List<MilestoneDef> milestones,
                List<ConditionDef> conditions,
                String zoneId
        ) {
            this(id, title, value, unit, active, description, category, condition, targetCount, milestones, conditions, zoneId, null, null);
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

        List<IncentiveRuleDef> activeRules = loadActiveRules(partner);

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
        if (rule.milestones() != null && !rule.milestones().isEmpty()) {
            evaluateDailyTarget(partner, rule, periodDate);
            return;
        }
        if (rule.targetCount() != null && rule.targetCount() > 1) {
            evaluateDailyTarget(partner, rule, periodDate);
            return;
        }
        if (rule.value().compareTo(BigDecimal.ZERO) <= 0) return;
        awardIncentive(partner, rule.id(), rule.title(), rule.value(), "DELIVERY_ASSIGNMENT", assignmentId, orderId, periodDate);
    }

    private void evaluateDailyTarget(DeliveryPartner partner, IncentiveRuleDef rule, LocalDate today) {
        ZonedDateTime startOfDay = today.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfDay = today.atTime(LocalTime.MAX).atZone(ZONE_IST);

        // Verify mandatory login slot condition if configured and a shift is booked
        if (rule.conditions() != null && shiftBookingRepository != null && onlineSessionRepository != null) {
            boolean hasLoginSlot = rule.conditions().stream().anyMatch(c -> "LOGIN_SLOT".equalsIgnoreCase(c.type()));
            var bookings = shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partner.getId());
            boolean hasBookedShift = bookings != null && bookings.stream().anyMatch(b -> "BOOKED".equalsIgnoreCase(b.getStatus()));
            if (hasLoginSlot && hasBookedShift) {
                List<IncentiveConditionProgressDto> conditions = evaluateConditionsForPartner(partner, rule, today);
                boolean slotSatisfied = conditions != null && conditions.stream()
                        .filter(c -> "LOGIN_SLOT".equalsIgnoreCase(c.type()))
                        .allMatch(c -> "SATISFIED".equalsIgnoreCase(c.status()) || c.progressPercentage() >= 100.0);
                if (!slotSatisfied) {
                    log.info("Partner {} daily target reward pending: mandatory login slot condition not fulfilled", partner.getId());
                    return;
                }
            }
        }

        // Verify offer conditions eligibility (e.g. max order rejections)
        int maxRejections = 1;
        if (rule.conditions() != null) {
            for (ConditionDef c : rule.conditions()) {
                if ("ORDER_REJECTION".equalsIgnoreCase(c.type()) && c.maxRejections() != null) {
                    maxRejections = c.maxRejections();
                }
            }
        }
        long rejectedToday = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndAssignedAtBetween(
                partner.getId(),
                DeliveryAssignmentStatus.REJECTED,
                startOfDay.toInstant(),
                endOfDay.toInstant()
        );
        if (rejectedToday > maxRejections) {
            log.info("Partner {} daily target reward ineligible: rejected {} orders (max allowed: {})", partner.getId(), rejectedToday, maxRejections);
            return;
        }

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

        List<IncentiveRuleDef> activeRules = loadActiveRules(partner);
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

        List<IncentiveRuleDef> activeRules = loadActiveRules(partner);
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

            List<IncentiveConditionProgressDto> offerConditions = null;

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

                    offerConditions = evaluateConditionsForPartner(partner, rule, date);
                    boolean conditionsFailed = offerConditions != null && offerConditions.stream().anyMatch(c -> "FAILED".equalsIgnoreCase(c.status()));

                    if (rule.milestones() != null && !rule.milestones().isEmpty()) {
                        offerMilestones = new ArrayList<>();
                        boolean firstUnearnedSeen = false;
                        for (MilestoneDef m : rule.milestones()) {
                            boolean mEarned = !conditionsFailed && ((tripsToday >= m.trips()) || earnedMap.containsKey(rule.id() + "_m" + m.trips()));
                            int mRemaining = Math.max(0, m.trips() - (int) tripsToday);
                            String mStatus;
                            if (mEarned) {
                                mStatus = "Completed";
                            } else if (conditionsFailed) {
                                mStatus = "Locked";
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
                    offerMilestones,
                    offerConditions,
                    rule.creatorRole(),
                    rule.scope(),
                    rule.zoneId()
            ));
        }

        return list;
    }

    private List<IncentiveConditionProgressDto> evaluateConditionsForPartner(
            DeliveryPartner partner,
            IncentiveRuleDef rule,
            LocalDate date
    ) {
        List<IncentiveConditionProgressDto> result = new ArrayList<>();
        List<ConditionDef> conditionDefs = rule.conditions();
        if (conditionDefs == null || conditionDefs.isEmpty()) {
            conditionDefs = List.of(
                    new ConditionDef("loginSlot", "LOGIN_SLOT", "Mandatory Login: Complete below slot", "7pm - 12am", "4h 30m", "7pm - 12am", "4h 30m", 270, null),
                    new ConditionDef("orderRejection", "ORDER_REJECTION", "Other condition", "Do not reject", "more than 1 order", null, null, null, 1)
            );
        }

        LocalDate today = LocalDate.now(ZONE_IST);
        ZonedDateTime startOfDay = date.atStartOfDay(ZONE_IST);
        ZonedDateTime endOfDay = date.atTime(LocalTime.MAX).atZone(ZONE_IST);

        // Find active shift booking for partner
        DeliveryShiftBooking bookedShift = null;
        if (shiftBookingRepository != null && partner != null) {
            List<DeliveryShiftBooking> bookings = shiftBookingRepository.findByDeliveryPartnerIdOrderByBookedAtDesc(partner.getId());
            for (DeliveryShiftBooking b : bookings) {
                if ("BOOKED".equalsIgnoreCase(b.getStatus())) {
                    bookedShift = b;
                    break;
                }
            }
        }

        for (ConditionDef c : conditionDefs) {
            if ("LOGIN_SLOT".equalsIgnoreCase(c.type())) {
                String rawStartTime;
                String rawEndTime;
                String rawDuration;
                int defaultMinMinutes = c.minDurationMinutes() != null ? c.minDurationMinutes() : 270;

                if (bookedShift != null) {
                    rawStartTime = bookedShift.getStartTime();
                    rawEndTime = bookedShift.getEndTime();
                    rawDuration = bookedShift.getDurationHours();
                } else {
                    String configuredSlot = (c.timeSlot() != null && !c.timeSlot().isBlank()) ? c.timeSlot() : (c.value() != null ? c.value() : "7pm - 12am");
                    String[] parts = configuredSlot.split("\\s*[-–—]\\s*");
                    rawStartTime = parts.length > 0 ? parts[0] : "7pm";
                    rawEndTime = parts.length > 1 ? parts[1] : "12am";
                    rawDuration = (c.duration() != null && !c.duration().isBlank()) ? c.duration() : (c.subtext() != null ? c.subtext() : "4h 30m");
                }

                String timeSlot = formatTimeSlotDisplay(rawStartTime, rawEndTime);
                int requiredMinutes = parseDurationMinutes(rawDuration, defaultMinMinutes);
                String requiredDurationStr = formatDurationDisplay(requiredMinutes);

                Instant[] bounds = getShiftBounds(date, rawStartTime, rawEndTime);
                Instant shiftStartInstant = bounds[0];
                Instant shiftEndInstant = bounds[1];

                long eligibleMinutes = calculateEligibleOnlineMinutes(partner, shiftStartInstant, shiftEndInstant);
                double progressPct = Math.min(100.0, ((double) eligibleMinutes / (double) requiredMinutes) * 100.0);

                Instant now = Instant.now();
                String status;
                String subtext;
                String progressText;

                if (progressPct >= 100.0) {
                    status = "SATISFIED";
                    subtext = requiredDurationStr;
                    progressText = "Slot fulfilled (" + requiredDurationStr + ")";
                } else if (now.isAfter(shiftEndInstant) || date.isBefore(today)) {
                    status = "FAILED";
                    subtext = (eligibleMinutes > 0) ? formatDurationDisplay(eligibleMinutes) : requiredDurationStr;
                    progressText = "Slot requirement not met";
                } else {
                    status = "PENDING";
                    subtext = (eligibleMinutes > 0) ? formatDurationDisplay(eligibleMinutes) : requiredDurationStr;
                    progressText = formatDurationDisplay(eligibleMinutes) + " of " + requiredDurationStr + " completed";
                }

                result.add(new IncentiveConditionProgressDto(
                        c.id(),
                        "LOGIN_SLOT",
                        c.title() != null ? c.title() : "Mandatory Login: Complete below slot",
                        timeSlot,
                        subtext,
                        status,
                        progressPct,
                        progressText
                ));
            } else if ("ORDER_REJECTION".equalsIgnoreCase(c.type())) {
                int maxRejections = c.maxRejections() != null ? c.maxRejections() : 1;
                long rejectedCount = deliveryAssignmentRepository.countByDeliveryPartnerIdAndStatusAndAssignedAtBetween(
                        partner.getId(),
                        DeliveryAssignmentStatus.REJECTED,
                        startOfDay.toInstant(),
                        endOfDay.toInstant()
                );

                String status;
                double progressPct;
                String progressText;

                if (rejectedCount > maxRejections) {
                    status = "FAILED";
                    progressPct = 100.0;
                    progressText = rejectedCount + " rejected (limit: " + maxRejections + ")";
                } else if (rejectedCount > 0) {
                    status = "PENDING";
                    progressPct = 50.0;
                    progressText = rejectedCount + " of " + maxRejections + " rejected";
                } else {
                    status = "SATISFIED";
                    progressPct = 100.0;
                    progressText = "0 orders rejected";
                }

                String val = (c.value() != null && !c.value().isBlank()) ? c.value() : "Do not reject";
                String sub = (c.subtext() != null && !c.subtext().isBlank()) ? c.subtext() : ("more than " + maxRejections + (maxRejections == 1 ? " order" : " orders"));

                result.add(new IncentiveConditionProgressDto(
                        c.id(),
                        "ORDER_REJECTION",
                        c.title() != null ? c.title() : "Other condition",
                        val,
                        sub,
                        status,
                        progressPct,
                        progressText
                ));
            } else {
                result.add(new IncentiveConditionProgressDto(
                        c.id(),
                        c.type() != null ? c.type() : "CUSTOM",
                        c.title(),
                        c.value(),
                        c.subtext(),
                        "SATISFIED",
                        100.0,
                        c.subtext()
                ));
            }
        }
        return result;
    }

    private long calculateEligibleOnlineMinutes(DeliveryPartner partner, Instant shiftStartInstant, Instant shiftEndInstant) {
        if (onlineSessionRepository == null || partner == null) {
            return 0;
        }
        List<DeliveryOnlineSession> sessions = onlineSessionRepository.findByDeliveryPartnerIdOrderByOnlineAtDesc(partner.getId());
        long totalSeconds = 0;
        Instant now = Instant.now();
        boolean hasOpenSession = false;

        for (DeliveryOnlineSession session : sessions) {
            Instant sStart = session.getOnlineAt();
            if (sStart == null) continue;
            Instant sEnd = session.getOfflineAt();
            if (sEnd == null) {
                hasOpenSession = true;
                sEnd = now;
            }

            Instant overlapStart = sStart.isAfter(shiftStartInstant) ? sStart : shiftStartInstant;
            Instant overlapEnd = sEnd.isBefore(shiftEndInstant) ? sEnd : shiftEndInstant;

            if (overlapEnd.isAfter(overlapStart)) {
                totalSeconds += java.time.Duration.between(overlapStart, overlapEnd).getSeconds();
            }
        }

        if (partner.isOnline() && !hasOpenSession) {
            Instant sStart = partner.getUpdatedAt() != null ? partner.getUpdatedAt() : now;
            Instant overlapStart = sStart.isAfter(shiftStartInstant) ? sStart : shiftStartInstant;
            Instant overlapEnd = now.isBefore(shiftEndInstant) ? now : shiftEndInstant;
            if (overlapEnd.isAfter(overlapStart)) {
                totalSeconds += java.time.Duration.between(overlapStart, overlapEnd).getSeconds();
            }
        }

        return totalSeconds / 60;
    }

    public static String formatTimeSlotDisplay(String startTime, String endTime) {
        String s = formatSingleTimeForCard(startTime);
        String e = formatSingleTimeForCard(endTime);
        if (s == null && e == null) return "7pm - 12am";
        if (s == null) return e;
        if (e == null) return s;
        return s + " - " + e;
    }

    public static String formatSingleTimeForCard(String t) {
        if (t == null || t.isBlank()) return null;
        String trimmed = t.trim();
        LocalTime lt = parseLocalTimeFlexible(trimmed);
        if (lt != null) {
            int hour = lt.getHour();
            int minute = lt.getMinute();
            String ampm = (hour >= 12) ? "pm" : "am";
            int h12 = hour % 12;
            if (h12 == 0) h12 = 12;
            if (minute == 0) {
                return h12 + ampm;
            } else {
                return String.format("%d:%02d%s", h12, minute, ampm);
            }
        }
        String clean = trimmed.toLowerCase().replaceAll("\\s+", "");
        clean = clean.replace(":00", "");
        if (clean.startsWith("0")) {
            clean = clean.substring(1);
        }
        return clean;
    }

    public static LocalTime parseLocalTimeFlexible(String t) {
        if (t == null || t.isBlank()) return null;
        String s = t.trim().toUpperCase();
        Matcher m12 = Pattern.compile("^(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)$").matcher(s);
        if (m12.matches()) {
            int hour = Integer.parseInt(m12.group(1));
            int minute = (m12.group(2) != null) ? Integer.parseInt(m12.group(2)) : 0;
            String ampm = m12.group(3);
            if ("PM".equals(ampm) && hour < 12) hour += 12;
            if ("AM".equals(ampm) && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        }
        Matcher m24 = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").matcher(s);
        if (m24.matches()) {
            int hour = Integer.parseInt(m24.group(1));
            int minute = Integer.parseInt(m24.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return LocalTime.of(hour, minute);
            }
        }
        return null;
    }

    public static Instant[] getShiftBounds(LocalDate date, String startTimeStr, String endTimeStr) {
        LocalTime startLt = parseLocalTimeFlexible(startTimeStr);
        LocalTime endLt = parseLocalTimeFlexible(endTimeStr);
        if (startLt == null) startLt = LocalTime.of(19, 0); // 7:00 PM
        if (endLt == null) endLt = LocalTime.of(0, 0);      // 12:00 AM

        ZonedDateTime startZdt = date.atTime(startLt).atZone(ZONE_IST);
        ZonedDateTime endZdt;
        if (!endLt.isAfter(startLt)) {
            endZdt = date.plusDays(1).atTime(endLt).atZone(ZONE_IST);
        } else {
            endZdt = date.atTime(endLt).atZone(ZONE_IST);
        }
        return new Instant[]{startZdt.toInstant(), endZdt.toInstant()};
    }

    public static int parseDurationMinutes(String durationStr, int defaultMinutes) {
        if (durationStr == null || durationStr.isBlank()) return defaultMinutes;
        String s = durationStr.trim().toLowerCase();
        Matcher mHm = Pattern.compile("(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?").matcher(s);
        if (mHm.matches() && (mHm.group(1) != null || mHm.group(2) != null)) {
            int h = mHm.group(1) != null ? Integer.parseInt(mHm.group(1)) : 0;
            int m = mHm.group(2) != null ? Integer.parseInt(mHm.group(2)) : 0;
            int total = h * 60 + m;
            if (total > 0) return total;
        }
        Matcher mDec = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*h?$").matcher(s);
        if (mDec.matches()) {
            double hrs = Double.parseDouble(mDec.group(1));
            int total = (int) Math.round(hrs * 60.0);
            if (total > 0) return total;
        }
        return defaultMinutes;
    }

    public static String formatDurationDisplay(long minutes) {
        if (minutes <= 0) return "0m";
        long h = minutes / 60;
        long m = minutes % 60;
        if (h > 0 && m > 0) {
            return h + "h " + m + "m";
        } else if (h > 0) {
            return h + "h";
        } else {
            return m + "m";
        }
    }

    private boolean matchZoneEntity(LocationZone z, String query) {
        if (z == null || query == null || query.isBlank()) return false;
        String q = query.trim().toLowerCase();
        if (z.getId() != null && z.getId().trim().equalsIgnoreCase(q)) return true;
        if (z.getZoneName() != null && z.getZoneName().trim().equalsIgnoreCase(q)) return true;
        if (z.getCityName() != null && z.getCityName().trim().equalsIgnoreCase(q)) return true;

        String cleanQ = q.replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim();
        String cleanId = z.getId() != null ? z.getId().trim().toLowerCase().replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim() : "";
        String cleanName = z.getZoneName() != null ? z.getZoneName().trim().toLowerCase().replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim() : "";
        String cleanCity = z.getCityName() != null ? z.getCityName().trim().toLowerCase().replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim() : "";

        if (!cleanQ.isEmpty()) {
            if (cleanQ.equalsIgnoreCase(cleanId) || cleanQ.equalsIgnoreCase(cleanName) || cleanQ.equalsIgnoreCase(cleanCity)) {
                return true;
            }
        }
        return false;
    }

    public boolean isZoneMatch(String ruleZone, String partnerZone) {
        if (ruleZone == null || partnerZone == null) return false;
        String r = ruleZone.trim().toLowerCase();
        String p = partnerZone.trim().toLowerCase();
        if (r.isEmpty() || p.isEmpty()) return false;
        if (r.equals(p)) return true;

        if (locationZoneRepository != null) {
            try {
                List<LocationZone> zones = locationZoneRepository.findAll();
                LocationZone rZone = zones.stream().filter(z -> matchZoneEntity(z, r)).findFirst().orElse(null);
                LocationZone pZone = zones.stream().filter(z -> matchZoneEntity(z, p)).findFirst().orElse(null);
                if (rZone != null && pZone != null) {
                    return rZone.getId().equalsIgnoreCase(pZone.getId());
                }
            } catch (Exception ignored) {}
        }

        String cleanR = r.replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "");
        String cleanP = p.replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "");
        if (!cleanR.isBlank() && cleanR.equals(cleanP)) {
            return true;
        }

        return AdminAccess.areZonesEquivalent(r, p);
    }

    public String resolvePartnerZone(DeliveryPartner partner) {
        if (partner == null) return null;
        if (partner.getZoneId() != null && !partner.getZoneId().isBlank()) {
            return partner.getZoneId().trim();
        }
        if (partner.getZoneName() != null && !partner.getZoneName().isBlank()) {
            return partner.getZoneName().trim();
        }
        if (partner.getCity() != null && !partner.getCity().isBlank()) {
            return partner.getCity().trim();
        }
        return null;
    }

    private IncentiveRuleDef parseRuleFromJson(JsonNode item, String fallbackZoneId) {
        if (item == null || !item.isObject()) return null;
        String id = item.has("id") ? item.get("id").asText("") : "";
        if (id.isBlank()) return null;

        String title = item.has("title") ? item.get("title").asText("") : "";
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

        String ruleZoneId = item.has("zoneId") && !item.get("zoneId").isNull() && !item.get("zoneId").asText().isBlank()
                ? item.get("zoneId").asText().trim()
                : fallbackZoneId;

        String creatorRole = item.has("creatorRole") && !item.get("creatorRole").isNull() && !item.get("creatorRole").asText().isBlank()
                ? item.get("creatorRole").asText().trim()
                : null;
        String scope = item.has("scope") && !item.get("scope").isNull() && !item.get("scope").asText().isBlank()
                ? item.get("scope").asText().trim()
                : null;

        if (scope == null) {
            scope = (ruleZoneId != null && !ruleZoneId.isBlank() && !"ALL".equalsIgnoreCase(ruleZoneId)) ? "ZONE" : "GLOBAL";
        }
        if (creatorRole == null) {
            creatorRole = "ZONE".equalsIgnoreCase(scope) ? "ZONE_ADMIN" : "SUPER_ADMIN";
        }
        if ("GLOBAL".equalsIgnoreCase(scope) && (ruleZoneId == null || "ALL".equalsIgnoreCase(ruleZoneId))) {
            ruleZoneId = null;
        }

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

        List<ConditionDef> conditions = null;
        if (item.has("conditions") && item.get("conditions").isArray()) {
            conditions = new ArrayList<>();
            for (JsonNode cNode : item.get("conditions")) {
                String cId = cNode.has("id") ? cNode.get("id").asText() : UUID.randomUUID().toString();
                String cType = cNode.has("type") ? cNode.get("type").asText() : "CUSTOM";
                String cTitle = cNode.has("title") ? cNode.get("title").asText() : "";
                String cVal = cNode.has("value") ? cNode.get("value").asText() : (cNode.has("timeSlot") ? cNode.get("timeSlot").asText() : "");
                String cSub = cNode.has("subtext") ? cNode.get("subtext").asText() : (cNode.has("duration") ? cNode.get("duration").asText() : "");
                String cTimeSlot = cNode.has("timeSlot") ? cNode.get("timeSlot").asText() : cVal;
                String cDuration = cNode.has("duration") ? cNode.get("duration").asText() : cSub;
                Integer cMinDur = cNode.has("minDurationMinutes") ? cNode.get("minDurationMinutes").asInt() : null;
                Integer cMaxRej = cNode.has("maxRejections") ? cNode.get("maxRejections").asInt() : null;
                conditions.add(new ConditionDef(cId, cType, cTitle, cVal, cSub, cTimeSlot, cDuration, cMinDur, cMaxRej));
            }
        } else if ("dailyTargetBonus".equalsIgnoreCase(id)) {
            conditions = List.of(
                    new ConditionDef("loginSlot", "LOGIN_SLOT", "Mandatory Login: Complete below slot", "7pm - 12am", "4h 30m", "7pm - 12am", "4h 30m", 270, null),
                    new ConditionDef("orderRejection", "ORDER_REJECTION", "Other condition", "Do not reject", "more than 1 order", null, null, null, 1)
            );
        }

        return new IncentiveRuleDef(id, title, value, unit, active, desc, category, condition, targetCount, milestones, conditions, ruleZoneId, creatorRole, scope);
    }

    private List<IncentiveRuleDef> loadActiveRules() {
        return loadActiveRules(null);
    }

    private List<IncentiveRuleDef> loadActiveRules(DeliveryPartner partner) {
        List<IncentiveRuleDef> rules = new ArrayList<>();
        boolean configFound = false;
        try {
            DeliveryPricingConfig config = pricingConfigRepository.findById(DEFAULT_CONFIG_ID).orElse(null);
            if (config != null && config.getConfigData() != null && !config.getConfigData().isBlank()) {
                JsonNode root = objectMapper.readTree(config.getConfigData());

                String partnerZoneId = resolvePartnerZone(partner);

                // 1. Universal Incentives (Created by Super Admin, displayed across all zones)
                List<IncentiveRuleDef> universalRules = new ArrayList<>();
                JsonNode universalNode = null;
                if (root.has("universalConfig") && root.get("universalConfig").has("incentives") && root.get("universalConfig").get("incentives").isArray()) {
                    universalNode = root.get("universalConfig").get("incentives");
                    configFound = true;
                } else if (root.has("incentives") && root.get("incentives").isArray()) {
                    universalNode = root.get("incentives");
                    configFound = true;
                }

                if (universalNode != null && universalNode.isArray()) {
                    for (JsonNode item : universalNode) {
                        IncentiveRuleDef rule = parseRuleFromJson(item, null);
                        if (rule != null) {
                            // If an individual rule in universal has a specific zone assigned, only show to partners in that zone
                            if (rule.zoneId() != null && !rule.zoneId().isBlank()
                                    && !"ALL".equalsIgnoreCase(rule.zoneId()) && !"UNIVERSAL".equalsIgnoreCase(rule.zoneId()) && !"GLOBAL".equalsIgnoreCase(rule.scope())) {
                                if (partnerZoneId != null && isZoneMatch(rule.zoneId(), partnerZoneId)) {
                                    universalRules.add(rule);
                                }
                            } else {
                                universalRules.add(rule);
                            }
                        }
                    }
                }

                // 2. Zone-Specific Incentives (Created by Zone Admin for a specific zone)
                List<IncentiveRuleDef> zoneRules = new ArrayList<>();
                if (partnerZoneId != null && root.has("zoneConfigs") && !root.get("zoneConfigs").isNull()) {
                    JsonNode zoneConfigs = root.get("zoneConfigs");
                    for (var it = zoneConfigs.fieldNames(); it.hasNext(); ) {
                        String zKey = it.next();
                        if (isZoneMatch(zKey, partnerZoneId)) {
                            JsonNode zNode = zoneConfigs.get(zKey);
                            if (zNode.has("incentives") && zNode.get("incentives").isArray()) {
                                configFound = true;
                                for (JsonNode item : zNode.get("incentives")) {
                                    IncentiveRuleDef rule = parseRuleFromJson(item, zKey);
                                    if (rule != null) {
                                        // Include only if rule belongs to this matching zone
                                        if (rule.zoneId() == null || rule.zoneId().isBlank() || isZoneMatch(rule.zoneId(), partnerZoneId)) {
                                            zoneRules.add(rule);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Merge: Super Admin universal incentives + partner's Zone-specific incentives
                // Zone-specific rules override universal rules with matching ID for that zone;
                // new zone-specific incentives are added to the list.
                Map<String, IncentiveRuleDef> merged = new LinkedHashMap<>();
                for (IncentiveRuleDef uRule : universalRules) {
                    merged.put(uRule.id(), uRule);
                }
                for (IncentiveRuleDef zRule : zoneRules) {
                    merged.put(zRule.id(), zRule);
                }

                rules.addAll(merged.values());
            }
        } catch (Exception e) {
            log.warn("Failed to parse incentive rules from configData: {}", e.getMessage());
        }

        if (rules.isEmpty() && !configFound) {
            rules.addAll(defaultRules());
        }
        return rules;
    }

    private List<IncentiveRuleDef> defaultRules() {
        return List.of(
                new IncentiveRuleDef("basePay", "Base Pay per Order", new BigDecimal("50.00"), "₹ / order", true, "Standard baseline compensation per fulfilled delivery assignment.", "Base & Distance", "", 1, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("peakHourBonus", "Peak Hour Bonus", new BigDecimal("100.00"), "₹ / order", true, "Extra surge payout during high-demand meal hours (12 PM–3 PM & 7 PM–11 PM).", "Weather & Surge", "PEAK_HOUR", 1, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("dailyTargetBonus", "Daily Target Bonus", new BigDecimal("150.00"), "₹ / day", true, "Bonus awarded upon completing daily order targets.", "Target & Mileage", "DAILY_TARGET", 15,
                        List.of(
                                new MilestoneDef(6, new BigDecimal("50.00")),
                                new MilestoneDef(9, new BigDecimal("75.00")),
                                new MilestoneDef(13, new BigDecimal("120.00")),
                                new MilestoneDef(18, new BigDecimal("190.00"))
                        ),
                        List.of(
                                new ConditionDef("loginSlot", "LOGIN_SLOT", "Mandatory Login: Complete below slot", "7pm - 12am", "4h 30m", "7pm - 12am", "4h 30m", 270, null),
                                new ConditionDef("orderRejection", "ORDER_REJECTION", "Other condition", "Do not reject", "more than 1 order", null, null, null, 1)
                        ),
                        null, "SUPER_ADMIN", "GLOBAL"
                ),
                new IncentiveRuleDef("weeklyTargetBonus", "Weekly Target Bonus", new BigDecimal("800.00"), "₹ / week", true, "Tier-1 weekly payout bonus for completing 80+ deliveries per week.", "Target & Mileage", "WEEKLY_TARGET", 80, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("longDistanceBonus", "Long-Distance Bonus", new BigDecimal("15.00"), "₹ / extra km", true, "Additional mileage incentive for deliveries exceeding 5 km radius.", "Base & Distance", "LONG_DISTANCE", 1, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("rainBonus", "Rain/Bad Weather Bonus", new BigDecimal("70.00"), "₹ / order", true, "Weather surge bonus automatically applied during rain or severe weather conditions.", "Weather & Surge", "BAD_WEATHER", 1, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("referralBonus", "Referral Bonus", new BigDecimal("500.00"), "₹ / referral", true, "Onboarding reward paid after referred delivery partner completes 25 orders.", "Reward & Rating", "REFERRAL", 25, null, null, null, "SUPER_ADMIN", "GLOBAL"),
                new IncentiveRuleDef("performanceBonus", "Performance/Rating Bonus", new BigDecimal("250.00"), "₹ / week", true, "Weekly quality incentive for maintaining customer rating of 4.85+ stars.", "Reward & Rating", "PERFORMANCE", 1, null, null, null, "SUPER_ADMIN", "GLOBAL")
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
