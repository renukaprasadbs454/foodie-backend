package com.foodie.wallet.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletBalanceResponseDto(
        UUID walletAccountId,
        BigDecimal balance,
        BigDecimal processingWithdrawals
) {
    public WalletBalanceResponseDto(UUID walletAccountId, BigDecimal balance) {
        this(walletAccountId, balance, BigDecimal.ZERO);
    }
}
