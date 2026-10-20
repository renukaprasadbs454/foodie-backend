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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        String actorRole = null;
        String actorZoneId = null;
        if (adminIdentityQueryPort != null && actorCredentialId != null) {
            adminUserId = adminIdentityQueryPort.findAdminUserIdByUserCredentialId(actorCredentialId)
                    .orElse(actorCredentialId);
            actorRole = adminIdentityQueryPort.findRoleNameByUserCredentialId(actorCredentialId).orElse(null);
            actorZoneId = adminIdentityQueryPort.findAssignedZoneIdByUserCredentialId(actorCredentialId).orElse(null);
        }

        boolean isSuperAdmin = (actorRole != null && "SUPER_ADMIN".equalsIgnoreCase(actorRole))
                || (actorRole == null && (actorZoneId == null || actorZoneId.isBlank()));
        boolean isZoneAdmin = !isSuperAdmin && (actorZoneId != null && !actorZoneId.isBlank());

        String pricingBasis = request.pricingBasis() != null && !request.pricingBasis().isBlank()
                ? request.pricingBasis()
                : (isZoneAdmin ? "ZONE" : config.getPricingBasis());

        String configDataJson = request.configData();
        if ((configDataJson == null || configDataJson.isBlank())
                && (request.universalConfig() != null || request.zoneConfigs() != null || request.zones() != null)) {
            try {
                Map<String, Object> payload = new HashMap<>();
                if (config.getConfigData() != null && !config.getConfigData().isBlank()) {
                    try {
                        Map<String, Object> existing = objectMapper.readValue(config.getConfigData(), Map.class);
                        if (existing != null) {
                            payload.putAll(existing);
                        }
                    } catch (Exception ignored) {}
                }
                payload.put("pricingBasis", pricingBasis);

                // Universal config can only be updated by Super Admin (not Zone Admin)
                if (!isZoneAdmin && request.universalConfig() != null) {
                    Map<String, Object> universalCopy = new HashMap<>();
                    if (request.universalConfig() instanceof Map<?, ?> uMap) {
                        universalCopy.putAll((Map<String, Object>) uMap);
                        if (uMap.containsKey("incentives")) {
                            List<Map<String, Object>> taggedIncentives = tagIncentives(uMap.get("incentives"), "SUPER_ADMIN", "GLOBAL", null);
                            if (taggedIncentives != null) {
                                universalCopy.put("incentives", taggedIncentives);
                                payload.put("incentives", taggedIncentives);
                            }
                        }
                    }
                    payload.put("universalConfig", universalCopy);
                }

                // Zone config handling with strict zone isolation
                if (request.zoneConfigs() != null) {
                    Object existingZones = payload.get("zoneConfigs");
                    Map<String, Object> mergedZones = new HashMap<>();
                    if (existingZones instanceof Map<?, ?> existingZoneMap) {
                        mergedZones.putAll((Map<String, Object>) existingZoneMap);
                    }

                    if (request.zoneConfigs() instanceof Map<?, ?> newZones) {
                        for (Map.Entry<?, ?> entry : newZones.entrySet()) {
                            String zKey = String.valueOf(entry.getKey());
                            Object zVal = entry.getValue();

                            // Zone Admin may ONLY modify their assigned zone!
                            if (isZoneAdmin && !isZoneEquivalent(zKey, actorZoneId)) {
                                log.warn("Zone Admin {} (zone {}) attempted unauthorized modification of zone {}",
                                        actorCredentialId, actorZoneId, zKey);
                                continue;
                            }

                            String creatorRole = isZoneAdmin ? "ZONE_ADMIN" : "SUPER_ADMIN";
                            String targetZoneId = isZoneAdmin ? actorZoneId : zKey;

                            if (zVal instanceof Map<?, ?> zMap) {
                                Map<String, Object> zMapCopy = new HashMap<>((Map<String, Object>) zMap);
                                if (zMap.containsKey("incentives")) {
                                    List<Map<String, Object>> taggedZoneIncentives = tagIncentives(
                                            zMap.get("incentives"), creatorRole, "ZONE", targetZoneId);
                                    if (taggedZoneIncentives != null) {
                                        zMapCopy.put("incentives", taggedZoneIncentives);
                                    }
                                }
                                mergedZones.put(zKey, zMapCopy);
                            } else {
                                mergedZones.put(zKey, zVal);
                            }
                        }
                    }
                    payload.put("zoneConfigs", mergedZones);
                }

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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tagIncentives(Object incentivesObj, String creatorRole, String scope, String zoneId) {
        if (!(incentivesObj instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> taggedList = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> itemMap) {
                Map<String, Object> copy = new HashMap<>((Map<String, Object>) itemMap);
                copy.put("creatorRole", creatorRole);
                copy.put("scope", scope);
                if (zoneId != null) {
                    copy.put("zoneId", zoneId);
                }
                taggedList.add(copy);
            }
        }
        return taggedList;
    }

    private boolean isZoneEquivalent(String zoneA, String zoneB) {
        if (zoneA == null || zoneB == null) return false;
        String a = zoneA.trim().toLowerCase();
        String b = zoneB.trim().toLowerCase();
        if (a.equals(b)) return true;
        String cleanA = a.replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim();
        String cleanB = b.replaceFirst("^(dz[-_]|zone[-_])", "").replaceAll("[-_]zone$", "").trim();
        if (!cleanA.isEmpty() && cleanA.equalsIgnoreCase(cleanB)) return true;
        return com.foodie.admin.security.AdminAccess.areZonesEquivalent(a, b);
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

    @SuppressWarnings("unchecked")
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
