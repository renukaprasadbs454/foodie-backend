package com.foodie.admin.repository;

import com.foodie.admin.entity.AdminZoneLoginHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminZoneLoginHistoryRepository extends JpaRepository<AdminZoneLoginHistory, UUID> {

    @Query("SELECT h FROM AdminZoneLoginHistory h WHERE " +
           "(:zoneId IS NULL OR :zoneId = '' OR h.zoneId = :zoneId) AND " +
           "(:search IS NULL OR :search = '' OR " +
           " LOWER(h.adminName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(h.adminEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(h.zoneName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY h.loginTime DESC")
    Page<AdminZoneLoginHistory> searchHistory(
            @Param("zoneId") String zoneId,
            @Param("search") String search,
            Pageable pageable
    );
}
