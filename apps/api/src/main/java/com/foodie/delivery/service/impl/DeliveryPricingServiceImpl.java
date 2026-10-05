package com.foodie.delivery.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.delivery.dto.request.UpdateDeliveryPricingRequestDto;
import com.foodie.delivery.dto.response.DeliveryPricingConfigResponseDto;
import com.foodie.delivery.entity.DeliveryPricingConfig;
import com.foodie.delivery.repository.DeliveryPricingConfigRepository;
import com.foodie.delivery.service.DeliveryPricingService;
import com.foodie.shared.contract.AdminIdentityQueryPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryPricingServiceImpl implements DeliveryPricingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPricingServiceImpl.class);
    private static final UUID DEFAULT_CONFIG_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final DeliveryPricingConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final AdminIdentityQueryPort adminIdentityQueryPort;

    public DeliveryPricingServiceImpl(
            DeliveryPricingConfigRepository configRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) AdminIdentityQueryPort adminIdentityQueryPort
    ) {
        this.configRepository = configRepository;
        this.objectMapper = objectMapper;
        this.adminIdentityQueryPort = adminIdentityQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryPricingConfigResponseDto getPricingConfig() {
        DeliveryPricingConfig config = getOrCreateConfig();
        return toDto(config);
    }

    @Override
    @Transactional
    public DeliveryPricingConfigResponseDto updatePricingConfig(UUID actorCredentialId, UpdateDeliveryPricingRequestDto request) {
        DeliveryPricingConfig config = getOrCreateConfig();

        UUID adminUserId = actorCredentialId;
        if (adminIdentityQueryPort != null && actorCredentialId != null) {
            adminUserId = adminIdentityQueryPort.findAdminUserIdByUserCredentialId(actorCredentialId)
                    .orElse(actorCredentialId);
        }

        String pricingBasis = request.pricingBasis() != null && !request.pricingBasis().isBlank()
                ? request.pricingBasis()
                : config.getPricingBasis();

        String configDataJson = request.configData();
        if ((configDataJson == null || configDataJson.isBlank())
                && (request.universalConfig() != null || request.zoneConfigs() != null || request.zones() != null)) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("pricingBasis", pricingBasis);
                if (request.universalConfig() != null) payload.put("universalConfig", request.universalConfig());
                if (request.zoneConfigs() != null) payload.put("zoneConfigs", request.zoneConfigs());
                if (request.zones() != null) payload.put("zones", request.zones());
                configDataJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                log.warn("Failed to serialize pricing config payload: {}", e.getMessage());
            }
        }

        config.update(
                request.minPricePerDelivery().setScale(2, RoundingMode.HALF_UP),
                request.moneyPerKm().setScale(2, RoundingMode.HALF_UP),
                pricingBasis,
                configDataJson,
                adminUserId
        );
        DeliveryPricingConfig saved = configRepository.save(config);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateDeliveryFee(Double distanceKm) {
        DeliveryPricingConfig config = getOrCreateConfig();
        BigDecimal minPrice = config.getMinPricePerDelivery();
        BigDecimal moneyPerKm = config.getMoneyPerKm();

        double dist = (distanceKm == null || distanceKm < 0) ? 0.0 : distanceKm;
        BigDecimal feeByDistance = moneyPerKm.multiply(BigDecimal.valueOf(dist)).setScale(2, RoundingMode.HALF_UP);

        // Display and payout whichever is greater: max(minPricePerDelivery, distance * moneyPerKm)
        return minPrice.max(feeByDistance).setScale(2, RoundingMode.HALF_UP);
    }

    private DeliveryPricingConfig getOrCreateConfig() {
        return configRepository.findById(DEFAULT_CONFIG_ID)
                .orElseGet(() -> configRepository.save(DeliveryPricingConfig.createDefault()));
    }

    private DeliveryPricingConfigResponseDto toDto(DeliveryPricingConfig config) {
        Object universalConfig = null;
        Map<String, Object> zoneConfigs = null;
        Object zones = null;

        if (config.getConfigData() != null && !config.getConfigData().isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(config.getConfigData());
                if (root.has("universalConfig")) {
                    universalConfig = objectMapper.treeToValue(root.get("universalConfig"), Object.class);
                }
                if (root.has("zoneConfigs")) {
                    zoneConfigs = objectMapper.treeToValue(root.get("zoneConfigs"), Map.class);
                }
                if (root.has("zones")) {
                    zones = objectMapper.treeToValue(root.get("zones"), Object.class);
                }
            } catch (Exception e) {
                log.warn("Failed to parse configData JSON: {}", e.getMessage());
            }
        }

        return new DeliveryPricingConfigResponseDto(
                config.getMinPricePerDelivery(),
                config.getMoneyPerKm(),
                config.getPricingBasis(),
                universalConfig,
                zoneConfigs,
                (zones instanceof java.util.List) ? (java.util.List<Object>) zones : null,
                config.getConfigData(),
                config.getUpdatedAt(),
                config.getUpdatedBy()
        );
    }
}
