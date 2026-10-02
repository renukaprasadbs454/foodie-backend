package com.foodie.delivery.dto.response;

import com.foodie.delivery.dto.response.DeliveryDocumentResponseDto;
import java.util.List;
import java.util.UUID;

public record DeliveryProfileResponseDto(
                UUID partnerId,
                String fullName,
                String vehicleType,
                String vehicleNumber,
                String addressLine1,
                String addressLine2,
                String city,
                String state,
                String pincode,
                String kycStatus,
                boolean isOnline,
                String profileImageUrl,
                List<DeliveryDocumentResponseDto> documents) {
}
