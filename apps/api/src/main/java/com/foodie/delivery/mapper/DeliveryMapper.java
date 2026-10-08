package com.foodie.delivery.mapper;

import com.foodie.delivery.dto.response.DeliveryAssignmentResponseDto;
import com.foodie.delivery.dto.response.DeliveryDocumentResponseDto;
import com.foodie.delivery.dto.response.DeliveryOfferResponseDto;
import com.foodie.delivery.dto.response.DeliveryProfileResponseDto;
import com.foodie.delivery.entity.DeliveryAssignment;
import com.foodie.delivery.entity.DeliveryPartner;
import com.foodie.delivery.entity.DeliveryPartnerDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DeliveryMapper {

        public DeliveryProfileResponseDto toProfile(DeliveryPartner partner, String profileImageUrl,
                        java.util.List<DeliveryDocumentResponseDto> documents) {
                return new DeliveryProfileResponseDto(
                                partner.getId(),
                                partner.getFullName(),
                                partner.getVehicleType().name(),
                                partner.getVehicleNumber(),
                                partner.getAddressLine1(),
                                partner.getAddressLine2(),
                                partner.getCity(),
                                partner.getState(),
                                partner.getPincode(),
                                partner.getKycStatus().name(),
                                partner.isOnline(),
                                profileImageUrl,
                                documents,
                                partner.getCashInHand());
        }

        public DeliveryDocumentResponseDto toDocument(DeliveryPartnerDocument document) {
                String key = document.getS3Key();
                String fileUrl = (key != null && !key.isBlank())
                                ? (key.startsWith("http://") || key.startsWith("https://") || key.startsWith("/") ? key : "/api/v1/storage/" + key)
                                : null;
                return new DeliveryDocumentResponseDto(
                                document.getId(),
                                document.getDocType().name(),
                                document.getVerificationStatus().name(),
                                key,
                                fileUrl,
                                document.getCreatedAt());
        }

        public DeliveryOfferResponseDto toOffer(
                        DeliveryAssignment assignment,
                        String orderNumber,
                        String restaurantName,
                        String pickupAddress,
                        String deliveryAddress,
                        java.time.Instant expectedFoodReadyTime,
                        Double estimatedDistance,
                        BigDecimal estimatedFee) {
                return new DeliveryOfferResponseDto(
                                assignment.getId(),
                                assignment.getOrderId(),
                                orderNumber,
                                restaurantName,
                                pickupAddress,
                                deliveryAddress,
                                expectedFoodReadyTime,
                                estimatedDistance,
                                estimatedFee);
        }

        public DeliveryAssignmentResponseDto toAssignment(DeliveryAssignment assignment) {
                boolean pickupOtpRequired = assignment.getStatus().name().equals("ACCEPTED")
                                && assignment.getPickupVerifiedAt() == null;
                return new DeliveryAssignmentResponseDto(
                                assignment.getId(),
                                assignment.getOrderId(),
                                assignment.getStatus().name(),
                                pickupOtpRequired,
                                assignment.getAssignedAt(),
                                assignment.getPickupVerifiedAt(),
                                assignment.getDeliveredVerifiedAt());
        }
}
