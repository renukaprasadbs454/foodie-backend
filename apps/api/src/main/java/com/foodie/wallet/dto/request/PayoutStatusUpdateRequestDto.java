package com.foodie.wallet.dto.request;

import com.foodie.common.enums.PayoutStatus;
import jakarta.validation.constraints.NotNull;

public record PayoutStatusUpdateRequestDto(
        @NotNull PayoutStatus status,
        String bankRef,
        String failureReason
) {
}
