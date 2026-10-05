package com.foodie.delivery.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeliveryPricingConfigResponseDto(
        BigDecimal minPricePerDelivery,
        BigDecimal moneyPerKm,
        String pricingBasis,
        Object universalConfig,
        Map<String, Object> zoneConfigs,
        List<Object> zones,
        String configData,
        Instant updatedAt,
        UUID updatedBy
) {
    public DeliveryPricingConfigResponseDto(
            BigDecimal minPricePerDelivery,
            BigDecimal moneyPerKm,
            Instant updatedAt,
            UUID updatedBy
    ) {
        this(minPricePerDelivery, moneyPerKm, "UNIVERSAL", null, null, null, null, updatedAt, updatedBy);
    }
}
