package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryPartnerIncentiveEarning;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryPartnerIncentiveEarningRepository extends JpaRepository<DeliveryPartnerIncentiveEarning, UUID> {

    List<DeliveryPartnerIncentiveEarning> findByDeliveryPartnerIdAndPeriodDate(UUID deliveryPartnerId, LocalDate periodDate);

    List<DeliveryPartnerIncentiveEarning> findByDeliveryPartnerIdAndPeriodDateBetween(
            UUID deliveryPartnerId, LocalDate fromDate, LocalDate toDate);

    List<DeliveryPartnerIncentiveEarning> findByDeliveryPartnerIdOrderByEarnedAtDesc(UUID deliveryPartnerId);

    boolean existsByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
            UUID deliveryPartnerId, String ruleId, String referenceType, UUID referenceId);

    Optional<DeliveryPartnerIncentiveEarning> findByDeliveryPartnerIdAndRuleIdAndReferenceTypeAndReferenceId(
            UUID deliveryPartnerId, String ruleId, String referenceType, UUID referenceId);

    @Query("SELECT COUNT(e) FROM DeliveryPartnerIncentiveEarning e WHERE e.deliveryPartner.id = :partnerId AND e.ruleId = :ruleId AND e.periodDate = :date")
    long countByDeliveryPartnerIdAndRuleIdAndPeriodDate(
            @Param("partnerId") UUID partnerId,
            @Param("ruleId") String ruleId,
            @Param("date") LocalDate date);

    @Query("SELECT COUNT(e) FROM DeliveryPartnerIncentiveEarning e WHERE e.deliveryPartner.id = :partnerId AND e.ruleId = :ruleId AND e.periodDate >= :fromDate AND e.periodDate <= :toDate")
    long countByDeliveryPartnerIdAndRuleIdAndPeriodDateBetween(
            @Param("partnerId") UUID partnerId,
            @Param("ruleId") String ruleId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
