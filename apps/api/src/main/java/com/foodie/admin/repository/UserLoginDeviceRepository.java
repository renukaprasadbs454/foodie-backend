package com.foodie.admin.repository;

import com.foodie.admin.entity.UserLoginDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserLoginDeviceRepository extends JpaRepository<UserLoginDevice, UUID> {

    List<UserLoginDevice> findByUserIdOrderByLastActiveAtDesc(String userId);

    Optional<UserLoginDevice> findFirstByUserIdAndDeviceNameAndBrowserOrderByLastActiveAtDesc(
            String userId,
            String deviceName,
            String browser
    );

    long countByUserIdAndIsCurrentActiveTrue(String userId);

    long countByUserId(String userId);

    List<UserLoginDevice> findByUserIdAndIsCurrentActiveTrue(String userId);

    @Modifying
    @Query("UPDATE UserLoginDevice d SET d.isCurrentActive = false, d.logoutAt = :logoutAt WHERE d.userId = :userId AND d.isCurrentActive = true")
    int logoutAllByUserId(@Param("userId") String userId, @Param("logoutAt") Instant logoutAt);

    void deleteByIdIn(List<UUID> ids);
}

