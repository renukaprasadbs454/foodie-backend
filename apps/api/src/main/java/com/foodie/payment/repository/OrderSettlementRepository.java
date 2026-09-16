package com.foodie.payment.repository;

import com.foodie.payment.entity.OrderSettlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSettlementRepository extends JpaRepository<OrderSettlement, UUID> {

    Optional<OrderSettlement> findByOrderId(UUID orderId);

    Optional<OrderSettlement> findByPaymentId(UUID paymentId);

    List<OrderSettlement> findByRestaurantId(UUID restaurantId);

    List<OrderSettlement> findByDeliveryPartnerId(UUID deliveryPartnerId);

    @Query("SELECT s FROM OrderSettlement s " +
           "WHERE (:restaurantId IS NULL OR s.restaurantId = :restaurantId) " +
           "AND (:deliveryPartnerId IS NULL OR s.deliveryPartnerId = :deliveryPartnerId) " +
           "AND (:settlementStatus IS NULL OR s.settlementStatus = :settlementStatus) " +
           "ORDER BY s.settledAt DESC")
    Page<OrderSettlement> search(
            @Param("restaurantId") UUID restaurantId,
            @Param("deliveryPartnerId") UUID deliveryPartnerId,
            @Param("settlementStatus") String settlementStatus,
            Pageable pageable
    );
}
