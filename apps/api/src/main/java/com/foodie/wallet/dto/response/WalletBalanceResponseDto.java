package com.foodie.wallet.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletBalanceResponseDto(
        UUID walletAccountId,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        BigDecimal totalEarnings,
        BigDecimal totalPayouts
) {
    public WalletBalanceResponseDto(UUID walletAccountId, BigDecimal balance) {
        this(walletAccountId, balance, balance, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
