package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryPartnerBankDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryPartnerBankDetailsRepository extends JpaRepository<DeliveryPartnerBankDetails, UUID> {
    Optional<DeliveryPartnerBankDetails> findByDeliveryPartnerId(UUID deliveryPartnerId);
}
