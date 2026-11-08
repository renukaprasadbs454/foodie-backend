package com.foodie.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foodie.common.enums.KycStatus;
import com.foodie.common.enums.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminDeliveryPartnerResponseDto(
        UUID id,
        UUID userCredentialId,
        String fullName,
        String phoneNumber,
        String email,
        VehicleType vehicleType,
        String vehicleNumber,
        String profileImageUrl,
        KycStatus kycStatus,
        String kycRejectionReason,
        boolean isOnline,
        BigDecimal cashInHand,
        BigDecimal maxCashInHandLimit,
        long totalDeliveries,
        String zone,
        List<DeliveryDocumentResponseDto> documents,
        Instant createdAt,
        Double latitude,
        Double longitude
) {
    public AdminDeliveryPartnerResponseDto(
            UUID id,
            UUID userCredentialId,
            String fullName,
            String phoneNumber,
            String email,
            VehicleType vehicleType,
            String vehicleNumber,
            String profileImageUrl,
            KycStatus kycStatus,
            String kycRejectionReason,
            boolean isOnline,
            BigDecimal cashInHand,
            BigDecimal maxCashInHandLimit,
            long totalDeliveries,
            String zone,
            List<DeliveryDocumentResponseDto> documents,
            Instant createdAt
    ) {
        this(id, userCredentialId, fullName, phoneNumber, email, vehicleType, vehicleNumber, profileImageUrl,
             kycStatus, kycRejectionReason, isOnline, cashInHand, maxCashInHandLimit, totalDeliveries, zone, documents, createdAt,
             null, null);
    }
    @JsonProperty("online")
    public boolean online() {
        return isOnline;
    }

    @JsonProperty("isAvailable")
    public boolean isAvailable() {
        return isOnline;
    }

    @JsonProperty("onlineStatus")
    public String onlineStatus() {
        return isOnline ? "ONLINE" : "OFFLINE";
    }
}
