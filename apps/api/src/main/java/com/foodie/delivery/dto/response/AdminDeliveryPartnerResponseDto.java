package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminDeliveryPartnerResponseDto(
        UUID id,
        UUID userCredentialId,
        String fullName,
        String phoneNumber,
        String vehicleType,
        String vehicleNumber,
        String profileImageUrl,
        String kycStatus,
        boolean isOnline,
        BigDecimal cashInHand,
        long totalDeliveries,
        String zone,
        List<DeliveryDocumentResponseDto> documents,
        Instant createdAt
) {
}
