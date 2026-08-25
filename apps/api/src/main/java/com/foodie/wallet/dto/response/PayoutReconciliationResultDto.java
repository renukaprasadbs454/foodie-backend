package com.foodie.wallet.dto.response;

import java.util.List;

public record PayoutReconciliationResultDto(
        int totalEvaluated,
        int matchedCount,
        int mismatchCount,
        List<ReconciliationItemDto> items
) {
}
