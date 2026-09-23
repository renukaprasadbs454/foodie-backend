package com.foodie.wallet.dto.response;

import com.foodie.common.enums.PayoutStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayoutResponseDto(
                UUID payoutId,
                PayoutStatus status,
                BigDecimal amount,
                String accountHolderName,
                String accountNumber,
                String ifscCode,
                String bankName,
                Instant requestedDate,
                Instant processedDate,
                String provider,
                String transactionId,
                String providerReference,
                String failureReason) {

        public PayoutResponseDto(
                        UUID payoutId,
                        PayoutStatus status,
                        BigDecimal amount,
                        String accountHolderName,
                        String accountNumber,
                        String ifscCode,
                        String bankName) {
                this(payoutId, status, amount, accountHolderName, accountNumber, ifscCode, bankName, null, null, null, null, null, null);
        }
}
