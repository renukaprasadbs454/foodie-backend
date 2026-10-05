package com.foodie.admin.dto.response;

import com.foodie.common.enums.LedgerEntryType;
import com.foodie.common.enums.LedgerReferenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminLedgerEntryResponseDto(
        UUID id,
        UUID walletAccountId,
        BigDecimal amount,
        LedgerEntryType entryType,
        LedgerReferenceType referenceType,
        UUID referenceId,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}
