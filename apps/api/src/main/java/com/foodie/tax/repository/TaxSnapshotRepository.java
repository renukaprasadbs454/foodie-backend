package com.foodie.tax.repository;

import com.foodie.tax.entity.TaxSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxSnapshotRepository extends JpaRepository<TaxSnapshot, UUID> {
    Optional<TaxSnapshot> findByOrderId(UUID orderId);
}
