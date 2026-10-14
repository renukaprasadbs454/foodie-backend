package com.foodie.delivery.repository;

import com.foodie.common.enums.KycStatus;
import com.foodie.delivery.entity.DeliveryPartner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, UUID> {

    Optional<DeliveryPartner> findByUserCredentialId(UUID userCredentialId);

    Optional<DeliveryPartner> findByIdAndUserCredentialId(UUID id, UUID userCredentialId);

    long countByKycStatus(KycStatus kycStatus);

    long countByOnlineTrue();

    java.util.List<DeliveryPartner> findByOnlineTrueOrderByUpdatedAtDesc();

    @Query(value = "SELECT p FROM DeliveryPartner p WHERE " +
           "((p.zoneId IS NOT NULL AND TRIM(p.zoneId) <> '') OR (p.zoneName IS NOT NULL AND TRIM(p.zoneName) <> '') OR p.zoneVerified = true) AND " +
           "EXISTS (SELECT d1 FROM DeliveryPartnerDocument d1 WHERE d1.deliveryPartner = p AND d1.docType = com.foodie.common.enums.DeliveryDocType.IDENTITY AND d1.s3Key IS NOT NULL AND TRIM(d1.s3Key) <> '') AND " +
           "EXISTS (SELECT d2 FROM DeliveryPartnerDocument d2 WHERE d2.deliveryPartner = p AND d2.docType = com.foodie.common.enums.DeliveryDocType.LICENSE AND d2.s3Key IS NOT NULL AND TRIM(d2.s3Key) <> '') AND " +
           "EXISTS (SELECT d3 FROM DeliveryPartnerDocument d3 WHERE d3.deliveryPartner = p AND d3.docType = com.foodie.common.enums.DeliveryDocType.VEHICLE_RC AND d3.s3Key IS NOT NULL AND TRIM(d3.s3Key) <> '') AND " +
           "(:kycStatus IS NULL OR p.kycStatus = :kycStatus) AND " +
           "(:search IS NULL OR LOWER(p.fullName) LIKE :search " +
           "OR LOWER(p.vehicleNumber) LIKE :search " +
           "OR EXISTS (SELECT u FROM com.foodie.auth.entity.UserCredential u WHERE u.id = p.userCredentialId AND (LOWER(u.phoneNumber) LIKE :search OR LOWER(u.email) LIKE :search)))",
           countQuery = "SELECT COUNT(p) FROM DeliveryPartner p WHERE " +
           "((p.zoneId IS NOT NULL AND TRIM(p.zoneId) <> '') OR (p.zoneName IS NOT NULL AND TRIM(p.zoneName) <> '') OR p.zoneVerified = true) AND " +
           "EXISTS (SELECT d1 FROM DeliveryPartnerDocument d1 WHERE d1.deliveryPartner = p AND d1.docType = com.foodie.common.enums.DeliveryDocType.IDENTITY AND d1.s3Key IS NOT NULL AND TRIM(d1.s3Key) <> '') AND " +
           "EXISTS (SELECT d2 FROM DeliveryPartnerDocument d2 WHERE d2.deliveryPartner = p AND d2.docType = com.foodie.common.enums.DeliveryDocType.LICENSE AND d2.s3Key IS NOT NULL AND TRIM(d2.s3Key) <> '') AND " +
           "EXISTS (SELECT d3 FROM DeliveryPartnerDocument d3 WHERE d3.deliveryPartner = p AND d3.docType = com.foodie.common.enums.DeliveryDocType.VEHICLE_RC AND d3.s3Key IS NOT NULL AND TRIM(d3.s3Key) <> '') AND " +
           "(:kycStatus IS NULL OR p.kycStatus = :kycStatus) AND " +
           "(:search IS NULL OR LOWER(p.fullName) LIKE :search " +
           "OR LOWER(p.vehicleNumber) LIKE :search " +
           "OR EXISTS (SELECT u FROM com.foodie.auth.entity.UserCredential u WHERE u.id = p.userCredentialId AND (LOWER(u.phoneNumber) LIKE :search OR LOWER(u.email) LIKE :search)))")
    Page<DeliveryPartner> searchDeliveryPartners(
            @Param("kycStatus") KycStatus kycStatus,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT p FROM DeliveryPartner p WHERE " +
           "((p.zoneId IS NOT NULL AND TRIM(p.zoneId) <> '') OR (p.zoneName IS NOT NULL AND TRIM(p.zoneName) <> '') OR p.zoneVerified = true) AND " +
           "EXISTS (SELECT d1 FROM DeliveryPartnerDocument d1 WHERE d1.deliveryPartner = p AND d1.docType = com.foodie.common.enums.DeliveryDocType.IDENTITY AND d1.s3Key IS NOT NULL AND TRIM(d1.s3Key) <> '') AND " +
           "EXISTS (SELECT d2 FROM DeliveryPartnerDocument d2 WHERE d2.deliveryPartner = p AND d2.docType = com.foodie.common.enums.DeliveryDocType.LICENSE AND d2.s3Key IS NOT NULL AND TRIM(d2.s3Key) <> '') AND " +
           "EXISTS (SELECT d3 FROM DeliveryPartnerDocument d3 WHERE d3.deliveryPartner = p AND d3.docType = com.foodie.common.enums.DeliveryDocType.VEHICLE_RC AND d3.s3Key IS NOT NULL AND TRIM(d3.s3Key) <> '')")
    java.util.List<DeliveryPartner> findAllKycReadyPartners();
}
