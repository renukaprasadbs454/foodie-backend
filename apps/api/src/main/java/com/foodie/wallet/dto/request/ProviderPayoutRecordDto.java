package com.foodie.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProviderPayoutRecordDto(
        @NotNull UUID payoutId,
        @NotNull BigDecimal amount,
        @NotNull String status,
        String bankRef,
        Instant processedAt
) {
}
