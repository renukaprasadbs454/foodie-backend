package com.foodie.restaurant.repository;

import com.foodie.restaurant.entity.Restaurant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    boolean existsByOwnerUserCredentialId(UUID ownerUserCredentialId);

    Optional<Restaurant> findByOwnerUserCredentialId(UUID ownerUserCredentialId);

    @Query(
            value = """
                    SELECT r.* FROM restaurant r
                    WHERE r.status = 'APPROVED'
                      AND (CAST(:search AS text) IS NULL OR CAST(:search AS text) = ''
                           OR r.name ILIKE CONCAT('%', CAST(:search AS text), '%')
                           OR EXISTS (
                                SELECT 1 FROM unnest(r.cuisine_types) c
                                WHERE c ILIKE CONCAT('%', CAST(:search AS text), '%')
                           ))
                      AND (CAST(:cuisineType AS text) IS NULL OR CAST(:cuisineType AS text) = ''
                           OR CAST(:cuisineType AS text) = ANY (r.cuisine_types))
                      AND (:minRating IS NULL OR r.avg_rating >= CAST(:minRating AS numeric))
                    """,
            countQuery = """
                    SELECT count(*) FROM restaurant r
                    WHERE r.status = 'APPROVED'
                      AND (CAST(:search AS text) IS NULL OR CAST(:search AS text) = ''
                           OR r.name ILIKE CONCAT('%', CAST(:search AS text), '%')
                           OR EXISTS (
                                SELECT 1 FROM unnest(r.cuisine_types) c
                                WHERE c ILIKE CONCAT('%', CAST(:search AS text), '%')
                           ))
                      AND (CAST(:cuisineType AS text) IS NULL OR CAST(:cuisineType AS text) = ''
                           OR CAST(:cuisineType AS text) = ANY (r.cuisine_types))
                      AND (:minRating IS NULL OR r.avg_rating >= CAST(:minRating AS numeric))
                    """,
            nativeQuery = true
    )
    Page<Restaurant> searchApproved(
            @Param("search") String search,
            @Param("cuisineType") String cuisineType,
            @Param("minRating") BigDecimal minRating,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT r.* FROM restaurant r
                    WHERE r.status = 'APPROVED'
                      AND (CAST(:search AS text) IS NULL OR CAST(:search AS text) = ''
                           OR r.name ILIKE CONCAT('%', CAST(:search AS text), '%')
                           OR EXISTS (
                                SELECT 1 FROM unnest(r.cuisine_types) c
                                WHERE c ILIKE CONCAT('%', CAST(:search AS text), '%')
                           ))
                      AND (CAST(:cuisineType AS text) IS NULL OR CAST(:cuisineType AS text) = ''
                           OR CAST(:cuisineType AS text) = ANY (r.cuisine_types))
                      AND (:minRating IS NULL OR r.avg_rating >= CAST(:minRating AS numeric))
                    ORDER BY (6371 * acos(
                               LEAST(1.0, GREATEST(-1.0,
                                   cos(radians(CAST(:lat AS double precision)))
                                   * cos(radians(r.latitude))
                                   * cos(radians(r.longitude) - radians(CAST(:lng AS double precision)))
                                   + sin(radians(CAST(:lat AS double precision)))
                                   * sin(radians(r.latitude))
                               ))
                           )) ASC,
                           r.created_at DESC
                    """,
            countQuery = """
                    SELECT count(*) FROM restaurant r
                    WHERE r.status = 'APPROVED'
                      AND (CAST(:search AS text) IS NULL OR CAST(:search AS text) = ''
                           OR r.name ILIKE CONCAT('%', CAST(:search AS text), '%')
                           OR EXISTS (
                                SELECT 1 FROM unnest(r.cuisine_types) c
                                WHERE c ILIKE CONCAT('%', CAST(:search AS text), '%')
                           ))
                      AND (CAST(:cuisineType AS text) IS NULL OR CAST(:cuisineType AS text) = ''
                           OR CAST(:cuisineType AS text) = ANY (r.cuisine_types))
                      AND (:minRating IS NULL OR r.avg_rating >= CAST(:minRating AS numeric))
                    """,
            nativeQuery = true
    )
    Page<Restaurant> searchApprovedGeo(
            @Param("search") String search,
            @Param("cuisineType") String cuisineType,
            @Param("minRating") BigDecimal minRating,
            @Param("lat") double lat,
            @Param("lng") double lng,
            Pageable pageable
    );
}
