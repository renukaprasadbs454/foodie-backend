package com.foodie.admin.repository;

import com.foodie.admin.entity.UnserviceableRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnserviceableRequestRepository extends JpaRepository<UnserviceableRequest, String> {

    List<UnserviceableRequest> findAllByOrderByCreatedAtDesc();

    Optional<UnserviceableRequest> findFirstByDeliveryPartnerId(UUID deliveryPartnerId);

    Optional<UnserviceableRequest> findFirstByDeliveryPartnerIdAndStatus(UUID deliveryPartnerId, String status);

    Optional<UnserviceableRequest> findFirstByRestaurantId(UUID restaurantId);

    Optional<UnserviceableRequest> findFirstByContactPhoneAndRequestSource(String contactPhone, String requestSource);

    Optional<UnserviceableRequest> findFirstByContactEmailAndRequestSource(String contactEmail, String requestSource);
}
