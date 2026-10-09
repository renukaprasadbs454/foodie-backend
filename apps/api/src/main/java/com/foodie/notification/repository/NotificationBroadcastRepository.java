package com.foodie.notification.repository;

import com.foodie.common.enums.NotificationDeliveryStatus;
import com.foodie.notification.entity.NotificationBroadcast;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, UUID> {

    @Query("SELECT b FROM NotificationBroadcast b WHERE (:audience IS NULL OR b.targetAudience = :audience OR b.targetAudience = 'ALL') ORDER BY b.createdAt DESC")
    Page<NotificationBroadcast> findByAudienceFilter(@Param("audience") String audience, Pageable pageable);

    List<NotificationBroadcast> findByDeliveryStatusAndScheduledAtLessThanEqual(
            NotificationDeliveryStatus status, Instant cutoff);
}
