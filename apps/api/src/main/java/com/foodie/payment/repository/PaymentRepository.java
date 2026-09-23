package com.foodie.payment.repository;

import com.foodie.payment.entity.Payment;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

        Optional<Payment> findByOrderId(UUID orderId);

        Optional<Payment> findByIdempotencyKey(String idempotencyKey);

        Optional<Payment> findByCashfreeOrderId(String cashfreeOrderId);

        Optional<Payment> findByPaymentSessionId(String paymentSessionId);

    @Modifying
    @Query("UPDATE Payment p SET p.status = com.foodie.common.enums.PaymentStatus.CAPTURED, p.cashfreeOrderId = COALESCE(:cfId, p.cashfreeOrderId), p.capturedAt = :now WHERE p.id = :id AND p.status IN (com.foodie.common.enums.PaymentStatus.PENDING, com.foodie.common.enums.PaymentStatus.FAILED)")
    int atomicMarkCaptured(@Param("id") UUID id, @Param("cfId") String cfId, @Param("now") Instant now);

    default int atomicMarkCaptured(UUID id, String cfId) {
        return atomicMarkCaptured(id, cfId, Instant.now());
    }

    @Modifying
    @Query("UPDATE Payment p SET p.status = com.foodie.common.enums.PaymentStatus.FAILED, p.cashfreeOrderId = COALESCE(:cfId, p.cashfreeOrderId) WHERE p.id = :id AND p.status = com.foodie.common.enums.PaymentStatus.PENDING")
    int atomicMarkFailed(@Param("id") UUID id, @Param("cfId") String cfId);
}
