package com.foodie.delivery.dto.response;

import com.foodie.common.enums.DocumentVerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryBankDetailsResponseDto(
        UUID id,
        UUID deliveryPartnerId,
        String accountHolderName,
        String accountNumber,
        String maskedAccountNumber,
        String ifscCode,
        String bankName,
        String branchName,
        String accountType,
        DocumentVerificationStatus verificationStatus,
        Instant verifiedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static String maskAccountNumber(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return "•••• •••• ••••";
        }
        String digitsOnly = rawNumber.trim();
        if (digitsOnly.length() <= 4) {
            return "•••• " + digitsOnly;
        }
        String lastFour = digitsOnly.substring(digitsOnly.length() - 4);
        return "•••• •••• " + lastFour;
    }
}
