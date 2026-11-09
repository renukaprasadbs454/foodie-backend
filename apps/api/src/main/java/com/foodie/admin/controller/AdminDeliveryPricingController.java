package com.foodie.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.dto.ApiResponse;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.security.principal.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/delivery-pricing")
@Tag(name = "Admin — Delivery Pricing")
public class AdminDeliveryPricingController {

    private static final UUID DEFAULT_CONFIG_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private final DeliveryPricingConfigRepository pricingConfigRepository;
    private final ObjectMapper objectMapper;

    public AdminDeliveryPricingController(
            DeliveryPricingConfigRepository pricingConfigRepository,
            ObjectMapper objectMapper) {
        this.pricingConfigRepository = pricingConfigRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OPS')")
    @Operation(summary = "Get delivery pricing configuration and incentive rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeliveryPricing() {
        DeliveryPricingConfig config = pricingConfigRepository.findById(DEFAULT_CONFIG_ID)
                .orElseGet(() -> {
                    DeliveryPricingConfig newConfig = DeliveryPricingConfig.createDefault();
                    return pricingConfigRepository.save(newConfig);
                });

        Map<String, Object> data = buildResponseMap(config);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OPS')")
    @Operation(summary = "Update delivery pricing configuration and incentive rules")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateDeliveryPricing(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return handleSave(principal, body);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OPS')")
    @Operation(summary = "Update delivery pricing configuration and incentive rules (alias)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> postDeliveryPricing(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        return handleSave(principal, body);
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> handleSave(
            AuthPrincipal principal,
            Map<String, Object> body) {
        DeliveryPricingConfig config = pricingConfigRepository.findById(DEFAULT_CONFIG_ID)
                .orElseGet(DeliveryPricingConfig::createDefault);

        BigDecimal minPrice = null;
        if (body.get("minPricePerDelivery") != null) {
            minPrice = new BigDecimal(body.get("minPricePerDelivery").toString());
        }

        BigDecimal moneyPerKm = null;
        if (body.get("moneyPerKm") != null) {
            moneyPerKm = new BigDecimal(body.get("moneyPerKm").toString());
        }

        String pricingBasis = body.get("pricingBasis") != null ? body.get("pricingBasis").toString() : "UNIVERSAL";

        String configDataStr = null;
        if (body.get("configData") != null && !body.get("configData").toString().isBlank()) {
            configDataStr = body.get("configData").toString();
        } else {
            try {
                configDataStr = objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                configDataStr = "{}";
            }
        }

        UUID adminId = (principal != null && principal.userId() != null) ? principal.userId() : null;
        config.update(minPrice, moneyPerKm, pricingBasis, configDataStr, adminId);
        DeliveryPricingConfig saved = pricingConfigRepository.save(config);

        return ResponseEntity.ok(ApiResponse.success(buildResponseMap(saved)));
    }

    private Map<String, Object> buildResponseMap(DeliveryPricingConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", config.getId());
        data.put("minPricePerDelivery", config.getMinPricePerDelivery());
        data.put("moneyPerKm", config.getMoneyPerKm());
        data.put("pricingBasis", config.getPricingBasis());
        data.put("configData", config.getConfigData());
        data.put("createdAt", config.getCreatedAt());
        data.put("updatedAt", config.getUpdatedAt());

        if (config.getConfigData() != null && !config.getConfigData().isBlank()) {
            try {
                JsonNode parsed = objectMapper.readTree(config.getConfigData());
                if (parsed.has("universalConfig")) {
                    data.put("universalConfig", objectMapper.convertValue(parsed.get("universalConfig"), Object.class));
                }
                if (parsed.has("zoneConfigs")) {
                    data.put("zoneConfigs", objectMapper.convertValue(parsed.get("zoneConfigs"), Object.class));
                }
                if (parsed.has("zones")) {
                    data.put("zones", objectMapper.convertValue(parsed.get("zones"), Object.class));
                }
            } catch (Exception ignored) {
            }
        }
        return data;
    }
}
