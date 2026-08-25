package com.foodie.wallet.dto.response;

import com.foodie.common.enums.PayoutStatus;
import com.foodie.common.enums.ReconciliationStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record ReconciliationItemDto(
        UUID payoutId,
        ReconciliationStatus reconciliationStatus,
        BigDecimal internalAmount,
        BigDecimal providerAmount,
        PayoutStatus internalStatus,
        String providerStatus,
        String remarks
) {
}
