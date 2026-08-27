package com.foodie.wallet.dto.response;

import java.math.BigDecimal;

public record CodBalanceResponseDto(
        BigDecimal totalCodCollected,
        BigDecimal totalCodDeposited,
        BigDecimal pendingCodDeposit
) {
}
