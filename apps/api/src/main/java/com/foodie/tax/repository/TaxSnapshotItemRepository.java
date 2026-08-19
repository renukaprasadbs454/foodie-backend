package com.foodie.tax.repository;

import com.foodie.tax.entity.TaxSnapshotItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxSnapshotItemRepository extends JpaRepository<TaxSnapshotItem, UUID> {
    List<TaxSnapshotItem> findByTaxSnapshotId(UUID taxSnapshotId);
}
