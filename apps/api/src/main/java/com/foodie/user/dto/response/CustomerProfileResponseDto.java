package com.foodie.user.dto.response;

import java.util.UUID;

public record CustomerProfileResponseDto(
        UUID customerId,
        String fullName,
        String email,
        String phoneNumber,
        UUID defaultAddressId,
        String accountStatus,
        Boolean isSuspended,
        String suspensionReason,
        Boolean isCodDisabled
) {
    public CustomerProfileResponseDto(
            UUID customerId,
            String fullName,
            String email,
            String phoneNumber,
            UUID defaultAddressId
    ) {
        this(customerId, fullName, email, phoneNumber, defaultAddressId, "ACTIVE", false, null, false);
    }
}
