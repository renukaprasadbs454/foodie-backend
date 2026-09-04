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

    boolean existsByUserCredentialId(UUID userCredentialId);

    long countByKycStatus(KycStatus kycStatus);

    long countByOnline(boolean online);

    Page<DeliveryPartner> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    Page<DeliveryPartner> findByFullNameContainingIgnoreCaseOrVehicleNumberContainingIgnoreCase(
            String fullName, String vehicleNumber, Pageable pageable);

    Page<DeliveryPartner> findByKycStatusAndFullNameContainingIgnoreCase(
            KycStatus kycStatus, String fullName, Pageable pageable);
}

