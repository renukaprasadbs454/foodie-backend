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

    @Query("SELECT p FROM DeliveryPartner p WHERE " +
           "(:kycStatus IS NULL OR p.kycStatus = :kycStatus) AND " +
           "(:search IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR EXISTS (SELECT u FROM com.foodie.auth.entity.UserCredential u WHERE u.id = p.userCredentialId AND (LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))))")
    Page<DeliveryPartner> searchDeliveryPartners(
            @Param("kycStatus") KycStatus kycStatus,
            @Param("search") String search,
            Pageable pageable);
}
