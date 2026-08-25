package com.foodie.wallet.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PayoutReconciliationRequestDto(
        @NotEmpty @Valid List<ProviderPayoutRecordDto> records
) {
}
