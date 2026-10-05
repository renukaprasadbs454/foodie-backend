package com.foodie.delivery.service;

import com.foodie.delivery.dto.response.IncentiveEarningHistoryDto;
import com.foodie.delivery.dto.response.IncentiveOfferProgressDto;
import com.foodie.delivery.dto.response.IncentivesProgressResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DeliveryIncentiveService {

    /**
     * Process completed delivery assignment and evaluate all active incentive rules.
     * Idempotent: safe against retries and duplicate events.
     */
    void processDeliveryCompletion(UUID deliveryPartnerId, UUID orderId, UUID assignmentId);

    /**
     * Get real-time incentives progress and applicable offers for a given calendar date.
     */
    IncentivesProgressResponseDto getIncentivesProgress(UUID userCredentialId, LocalDate date);

    /**
     * Get active incentive offers configured by Admin.
     */
    List<IncentiveOfferProgressDto> getActiveIncentives(UUID userCredentialId);

    /**
     * Get historical incentive earnings for the delivery partner.
     */
    List<IncentiveEarningHistoryDto> getIncentiveEarnings(UUID userCredentialId);
}
