package com.foodie.tax.service;

import com.foodie.tax.entity.TaxSnapshot;
import com.foodie.tax.model.TaxCalculationRequest;
import com.foodie.tax.model.TaxCalculationResult;
import java.util.Optional;
import java.util.UUID;

public interface TaxEngineService {
    TaxCalculationResult calculateAndSnapshot(TaxCalculationRequest request);
    Optional<TaxSnapshot> getSnapshotForOrder(UUID orderId);
}
