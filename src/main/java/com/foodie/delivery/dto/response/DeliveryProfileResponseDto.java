package com.foodie.delivery.dto.response;

import java.util.UUID;

public record DeliveryProfileResponseDto(
        UUID partnerId,
        String fullName,
        String vehicleType,
        String vehicleNumber,
        String kycStatus,
        boolean isOnline,
        String profileImageUrl
) {
}
