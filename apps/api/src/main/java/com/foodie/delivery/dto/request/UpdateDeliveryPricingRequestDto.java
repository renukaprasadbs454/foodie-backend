package com.foodie.delivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UpdateDeliveryPricingRequestDto(
        @NotNull(message = "minPricePerDelivery is required")
        @DecimalMin(value = "0.0", message = "minPricePerDelivery must be non-negative")
        BigDecimal minPricePerDelivery,

        @NotNull(message = "moneyPerKm is required")
        @DecimalMin(value = "0.0", message = "moneyPerKm must be non-negative")
        BigDecimal moneyPerKm,

        String pricingBasis,
        Object universalConfig,
        Map<String, Object> zoneConfigs,
        List<Object> zones,
        String configData
) {
    public UpdateDeliveryPricingRequestDto(BigDecimal minPricePerDelivery, BigDecimal moneyPerKm) {
        this(minPricePerDelivery, moneyPerKm, "UNIVERSAL", null, null, null, null);
    }
}
