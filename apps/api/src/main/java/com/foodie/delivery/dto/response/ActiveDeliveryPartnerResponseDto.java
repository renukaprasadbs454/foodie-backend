package com.foodie.delivery.dto.response;

import java.util.UUID;

public record ActiveDeliveryPartnerResponseDto(
        UUID partnerId,
        String fullName,
        String vehicleNumber,
        String mobileNumber,
        String signatureRating,
        int completedOrders
) {}
