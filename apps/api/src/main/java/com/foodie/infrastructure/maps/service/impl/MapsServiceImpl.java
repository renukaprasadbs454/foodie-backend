package com.foodie.infrastructure.maps.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.ExternalServiceException;
import com.foodie.common.exception.ResourceNotFoundException;
import com.foodie.infrastructure.maps.config.MapsProperties;
import com.foodie.infrastructure.maps.dto.response.GeocodeResponseDto;
import com.foodie.infrastructure.maps.dto.response.ReverseGeocodeResponseDto;
import com.foodie.infrastructure.maps.service.MapsService;
import com.foodie.infrastructure.maps.util.CoordinateValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MapsServiceImpl implements MapsService {

    private static final Logger log = LoggerFactory.getLogger(MapsServiceImpl.class);
    private static final String GEOCODE_CACHE_PREFIX = "maps:geocode:";
    private static final String REVERSE_GEOCODE_CACHE_PREFIX = "maps:reverse-geocode:";

    private final MapsProperties mapsProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;

    @Autowired
    public MapsServiceImpl(
            MapsProperties mapsProperties,
            ObjectMapper objectMapper,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.mapsProperties = mapsProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(mapsProperties.getTimeoutMs());
        requestFactory.setReadTimeout(mapsProperties.getTimeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(mapsProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public GeocodeResponseDto geocode(String address) {
        if (address == null || address.isBlank()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Address parameter must not be blank.");
        }

        String normalizedAddress = address.trim().toLowerCase(Locale.ROOT);
        String cacheKey = GEOCODE_CACHE_PREFIX + normalizedAddress;

        if (redisTemplate != null) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                if (cachedJson != null) {
                    return objectMapper.readValue(cachedJson, GeocodeResponseDto.class);
                }
            } catch (Exception ex) {
                log.warn("Redis read failure for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        GeocodeResponseDto result;
        if (!mapsProperties.isConfigured()) {
            log.info("Maps API key not configured or stub mode active. Returning mock geocode result for address: {}", address);
            result = createMockGeocodeResponse(address);
        } else {
            result = callGoogleGeocodeApi(address);
        }

        if (redisTemplate != null && result != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(
                        cacheKey,
                        json,
                        Duration.ofHours(mapsProperties.getCacheTtlHours())
                );
            } catch (Exception ex) {
                log.warn("Redis write failure for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        return result;
    }

    @Override
    public ReverseGeocodeResponseDto reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        CoordinateValidator.validate(latitude, longitude);

        BigDecimal roundedLat = latitude.setScale(4, RoundingMode.HALF_UP);
        BigDecimal roundedLng = longitude.setScale(4, RoundingMode.HALF_UP);
        String cacheKey = REVERSE_GEOCODE_CACHE_PREFIX + roundedLat + ":" + roundedLng;

        if (redisTemplate != null) {
            try {
                String cachedJson = redisTemplate.opsForValue().get(cacheKey);
                if (cachedJson != null) {
                    ReverseGeocodeResponseDto cached = objectMapper.readValue(cachedJson, ReverseGeocodeResponseDto.class);
                    // Retain exact requested coordinates
                    return new ReverseGeocodeResponseDto(
                            latitude,
                            longitude,
                            cached.formattedAddress(),
                            cached.city(),
                            cached.state(),
                            cached.country(),
                            cached.postalCode(),
                            cached.locality()
                    );
                }
            } catch (Exception ex) {
                log.warn("Redis read failure for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        ReverseGeocodeResponseDto result;
        if (!mapsProperties.isConfigured()) {
            log.info("Maps API key not configured or stub mode active. Returning mock reverse-geocode result for lat={}, lng={}", latitude, longitude);
            result = createMockReverseGeocodeResponse(latitude, longitude);
        } else {
            result = callGoogleReverseGeocodeApi(latitude, longitude);
        }

        if (redisTemplate != null && result != null) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(
                        cacheKey,
                        json,
                        Duration.ofHours(mapsProperties.getCacheTtlHours())
                );
            } catch (Exception ex) {
                log.warn("Redis write failure for key {}: {}", cacheKey, ex.getMessage());
            }
        }

        return result;
    }

    @Override
    public boolean isValidCoordinate(BigDecimal latitude, BigDecimal longitude) {
        return CoordinateValidator.isValid(latitude, longitude);
    }

    @Override
    public double calculateDistanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        CoordinateValidator.validate(lat1, lng1);
        CoordinateValidator.validate(lat2, lng2);

        double earthRadius = 6371.0; // Radius of the Earth in km
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private GeocodeResponseDto callGoogleGeocodeApi(String address) {
        try {
            String rawJson = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/json")
                            .queryParam("address", address)
                            .queryParam("key", mapsProperties.getApiKey())
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Empty response from Google Maps API.");
            }

            JsonNode root = objectMapper.readTree(rawJson);
            String status = root.path("status").asText("");

            if ("ZERO_RESULTS".equalsIgnoreCase(status)) {
                throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "No geocoding results found for address: " + address);
            }

            if (!"OK".equalsIgnoreCase(status)) {
                String errorMessage = root.path("error_message").asText(status);
                log.error("Google Maps Geocoding API error: status={}, message={}", status, errorMessage);
                throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Google Maps API error: " + status);
            }

            JsonNode firstResult = root.path("results").get(0);
            JsonNode locationNode = firstResult.path("geometry").path("location");

            BigDecimal lat = BigDecimal.valueOf(locationNode.path("lat").asDouble());
            BigDecimal lng = BigDecimal.valueOf(locationNode.path("lng").asDouble());
            String formattedAddress = firstResult.path("formatted_address").asText(address);

            String city = extractComponent(firstResult, "locality");
            if (city == null) {
                city = extractComponent(firstResult, "administrative_area_level_2");
            }
            String state = extractComponent(firstResult, "administrative_area_level_1");
            String country = extractComponent(firstResult, "country");
            String postalCode = extractComponent(firstResult, "postal_code");
            String locality = extractComponent(firstResult, "sublocality_level_1");
            if (locality == null) {
                locality = extractComponent(firstResult, "sublocality");
            }

            return new GeocodeResponseDto(
                    lat,
                    lng,
                    formattedAddress,
                    city != null ? city : "Bengaluru",
                    state != null ? state : "Karnataka",
                    country != null ? country : "India",
                    postalCode,
                    locality
            );
        } catch (ResourceNotFoundException | ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error invoking Google Maps Geocoding API for address: {}", address, ex);
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Maps service failed to process request.");
        }
    }

    private ReverseGeocodeResponseDto callGoogleReverseGeocodeApi(BigDecimal latitude, BigDecimal longitude) {
        try {
            String latlngStr = latitude.toPlainString() + "," + longitude.toPlainString();
            String rawJson = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/json")
                            .queryParam("latlng", latlngStr)
                            .queryParam("key", mapsProperties.getApiKey())
                            .build())
                    .retrieve()
                    .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Empty response from Google Maps API.");
            }

            JsonNode root = objectMapper.readTree(rawJson);
            String status = root.path("status").asText("");

            if ("ZERO_RESULTS".equalsIgnoreCase(status)) {
                throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "No reverse-geocoding results found for coordinates.");
            }

            if (!"OK".equalsIgnoreCase(status)) {
                String errorMessage = root.path("error_message").asText(status);
                log.error("Google Maps Reverse Geocoding API error: status={}, message={}", status, errorMessage);
                throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Google Maps API error: " + status);
            }

            JsonNode firstResult = root.path("results").get(0);
            String formattedAddress = firstResult.path("formatted_address").asText("Bengaluru, Karnataka, India");

            String city = extractComponent(firstResult, "locality");
            if (city == null) {
                city = extractComponent(firstResult, "administrative_area_level_2");
            }
            String state = extractComponent(firstResult, "administrative_area_level_1");
            String country = extractComponent(firstResult, "country");
            String postalCode = extractComponent(firstResult, "postal_code");
            String locality = extractComponent(firstResult, "sublocality_level_1");
            if (locality == null) {
                locality = extractComponent(firstResult, "sublocality");
            }

            return new ReverseGeocodeResponseDto(
                    latitude,
                    longitude,
                    formattedAddress,
                    city != null ? city : "Bengaluru",
                    state != null ? state : "Karnataka",
                    country != null ? country : "India",
                    postalCode,
                    locality
            );
        } catch (ResourceNotFoundException | ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error invoking Google Maps Reverse Geocoding API for coordinates: {},{}", latitude, longitude, ex);
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Maps service failed to process reverse-geocoding request.");
        }
    }

    private String extractComponent(JsonNode resultNode, String type) {
        JsonNode components = resultNode.path("address_components");
        if (components.isArray()) {
            for (JsonNode comp : components) {
                JsonNode typesNode = comp.path("types");
                if (typesNode.isArray()) {
                    for (JsonNode t : typesNode) {
                        if (type.equalsIgnoreCase(t.asText())) {
                            return comp.path("long_name").asText();
                        }
                    }
                }
            }
        }
        return null;
    }

    private GeocodeResponseDto createMockGeocodeResponse(String address) {
        // Fallback Bangalore coordinates: 12.9716, 77.5946
        BigDecimal defaultLat = new BigDecimal("12.971600");
        BigDecimal defaultLng = new BigDecimal("77.594600");

        String formatted = address.trim();
        if (!formatted.toLowerCase(Locale.ROOT).contains("bengaluru") && !formatted.toLowerCase(Locale.ROOT).contains("bangalore")) {
            formatted = formatted + ", Bengaluru, Karnataka, India";
        }

        return new GeocodeResponseDto(
                defaultLat,
                defaultLng,
                formatted,
                "Bengaluru",
                "Karnataka",
                "India",
                "560001",
                "Central Bengaluru"
        );
    }

    private ReverseGeocodeResponseDto createMockReverseGeocodeResponse(BigDecimal latitude, BigDecimal longitude) {
        return new ReverseGeocodeResponseDto(
                latitude,
                longitude,
                "Bengaluru, Karnataka, India",
                "Bengaluru",
                "Karnataka",
                "India",
                "560001",
                "Bengaluru"
        );
    }
}
