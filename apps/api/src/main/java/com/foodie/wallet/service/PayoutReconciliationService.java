package com.foodie.wallet.service;

import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.enums.ReconciliationStatus;
import com.foodie.wallet.dto.request.ProviderPayoutRecordDto;
import com.foodie.wallet.dto.response.PayoutReconciliationResultDto;
import com.foodie.wallet.dto.response.ReconciliationItemDto;
import com.foodie.wallet.entity.Payout;
import com.foodie.wallet.repository.PayoutRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutReconciliationService {

    private final PayoutRepository payoutRepository;

    public PayoutReconciliationService(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    @Transactional(readOnly = true)
    public PayoutReconciliationResultDto reconcile(List<ProviderPayoutRecordDto> providerRecords) {
        List<ReconciliationItemDto> items = new ArrayList<>();
        Set<UUID> seenProviderPayoutIds = new HashSet<>();
        Set<UUID> duplicateProviderIds = new HashSet<>();

        for (ProviderPayoutRecordDto rec : providerRecords) {
            if (rec.payoutId() != null) {
                if (!seenProviderPayoutIds.add(rec.payoutId())) {
                    duplicateProviderIds.add(rec.payoutId());
                }
            }
        }

        List<UUID> payoutIds = providerRecords.stream()
                .map(ProviderPayoutRecordDto::payoutId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<UUID, Payout> internalPayoutMap = payoutRepository.findAllById(payoutIds).stream()
                .collect(Collectors.toMap(Payout::getId, Function.identity()));

        int matchedCount = 0;
        int mismatchCount = 0;

        for (ProviderPayoutRecordDto rec : providerRecords) {
            UUID id = rec.payoutId();
            if (id != null && duplicateProviderIds.contains(id)) {
                items.add(new ReconciliationItemDto(
                        id,
                        ReconciliationStatus.DUPLICATE,
                        internalPayoutMap.containsKey(id) ? internalPayoutMap.get(id).getAmount() : null,
                        rec.amount(),
                        internalPayoutMap.containsKey(id) ? internalPayoutMap.get(id).getStatus() : null,
                        rec.status(),
                        "Duplicate provider record received for payout " + id
                ));
                mismatchCount++;
                continue;
            }

            Payout internal = internalPayoutMap.get(id);
            if (internal == null) {
                items.add(new ReconciliationItemDto(
                        id,
                        ReconciliationStatus.MISSING_PROVIDER_RECORD,
                        null,
                        rec.amount(),
                        null,
                        rec.status(),
                        "No internal payout found for provider payout ID " + id
                ));
                mismatchCount++;
                continue;
            }

            BigDecimal internalAmt = internal.getAmount();
            BigDecimal providerAmt = rec.amount();
            boolean amountMatched = internalAmt != null && providerAmt != null && internalAmt.compareTo(providerAmt) == 0;

            boolean statusMatched = isStatusCompatible(internal.getStatus(), rec.status());

            if (!amountMatched) {
                items.add(new ReconciliationItemDto(
                        id,
                        ReconciliationStatus.AMOUNT_MISMATCH,
                        internalAmt,
                        providerAmt,
                        internal.getStatus(),
                        rec.status(),
                        "Amount mismatch: internal=" + internalAmt + ", provider=" + providerAmt
                ));
                mismatchCount++;
            } else if (!statusMatched) {
                items.add(new ReconciliationItemDto(
                        id,
                        ReconciliationStatus.STATUS_MISMATCH,
                        internalAmt,
                        providerAmt,
                        internal.getStatus(),
                        rec.status(),
                        "Status mismatch: internal=" + internal.getStatus() + ", provider=" + rec.status()
                ));
                mismatchCount++;
            } else {
                items.add(new ReconciliationItemDto(
                        id,
                        ReconciliationStatus.MATCHED,
                        internalAmt,
                        providerAmt,
                        internal.getStatus(),
                        rec.status(),
                        "Payout records match"
                ));
                matchedCount++;
            }
        }

        return new PayoutReconciliationResultDto(
                items.size(),
                matchedCount,
                mismatchCount,
                items
        );
    }

    private boolean isStatusCompatible(PayoutStatus internalStatus, String providerStatusStr) {
        if (providerStatusStr == null) {
            return false;
        }
        String pStatus = providerStatusStr.toUpperCase().trim();
        switch (internalStatus) {
            case COMPLETED:
                return "COMPLETED".equals(pStatus) || "SUCCESS".equals(pStatus) || "PAID".equals(pStatus);
            case FAILED:
                return "FAILED".equals(pStatus) || "FAILURE".equals(pStatus) || "REJECTED".equals(pStatus);
            case REQUESTED:
            case PROCESSING:
                return "REQUESTED".equals(pStatus) || "PROCESSING".equals(pStatus) || "PENDING".equals(pStatus) || "INITIATED".equals(pStatus);
            default:
                return false;
        }
    }
}
