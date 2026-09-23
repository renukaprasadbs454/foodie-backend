package com.foodie.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpsertDeliveryBankDetailsRequestDto(
        @NotBlank(message = "Account holder name is required")
        String accountHolderName,

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be between 9 and 18 digits")
        String accountNumber,

        @NotBlank(message = "IFSC code is required")
        @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "Invalid IFSC code format (e.g., SBIN0001234)")
        String ifscCode,

        @NotBlank(message = "Bank name is required")
        String bankName,

        String branchName,

        String accountType
) {}
