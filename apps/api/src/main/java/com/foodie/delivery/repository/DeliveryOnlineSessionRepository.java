package com.foodie.delivery.repository;

import com.foodie.delivery.entity.DeliveryOnlineSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryOnlineSessionRepository extends JpaRepository<DeliveryOnlineSession, UUID> {

    List<DeliveryOnlineSession> findByDeliveryPartnerIdOrderByOnlineAtDesc(UUID deliveryPartnerId);

    Optional<DeliveryOnlineSession> findFirstByDeliveryPartnerIdAndOfflineAtIsNullOrderByOnlineAtDesc(UUID deliveryPartnerId);

    List<DeliveryOnlineSession> findByDeliveryPartnerIdAndOnlineAtGreaterThanEqualOrderByOnlineAtDesc(
            UUID deliveryPartnerId,
            Instant from
    );
}
