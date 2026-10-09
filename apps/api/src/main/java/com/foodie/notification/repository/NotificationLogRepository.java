package com.foodie.notification.repository;

import com.foodie.notification.entity.NotificationLog;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    boolean existsByUserCredentialIdAndTitleAndBody(UUID userCredentialId, String title, String body);

    Page<NotificationLog> findByUserCredentialId(UUID userCredentialId, Pageable pageable);

    Page<NotificationLog> findByUserCredentialIdAndReadAtIsNull(UUID userCredentialId, Pageable pageable);

    Optional<NotificationLog> findByIdAndUserCredentialId(UUID id, UUID userCredentialId);

    long countByUserCredentialIdAndReadAtIsNull(UUID userCredentialId);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.readAt = :now WHERE n.userCredentialId = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") UUID userId, @Param("now") Instant now);

    default int markAllAsRead(UUID userId) {
        return markAllAsRead(userId, Instant.now());
    }

    @Modifying
    @Query("DELETE FROM NotificationLog n WHERE n.sentAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
