package com.foodie.admin.controller;

import com.foodie.admin.dto.LocationZoneDto;
import com.foodie.admin.dto.CityDto;
import com.foodie.admin.dto.UnserviceableRequestDto;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.entity.City;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.repository.CityRepository;
import com.foodie.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/location")
public class LocationZoneController {

    @Autowired
    private LocationZoneRepository zoneRepository;

    @Autowired
    private CityRepository cityRepository;

    private final ConcurrentHashMap<String, UnserviceableRequestDto> unserviceableRequests = new ConcurrentHashMap<>();

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<LocationZoneDto>>> getAllZones() {
        List<LocationZoneDto> dtos = zoneRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PostMapping("/zones")
    @Transactional
    public ResponseEntity<ApiResponse<LocationZoneDto>> createZone(@RequestBody LocationZoneDto dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("dz-" + UUID.randomUUID().toString().substring(0, 6));
        }
        if (dto.getStatus() == null) {
            dto.setStatus("ACTIVE");
        }
        if (dto.getSurgeMultiplier() == null) {
            dto.setSurgeMultiplier(new BigDecimal("1.00"));
        }
        LocationZone entity = mapToEntity(dto);
        zoneRepository.saveAndFlush(entity);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PatchMapping("/zones/{zoneId}/toggles")
    @Transactional
    public ResponseEntity<ApiResponse<LocationZoneDto>> updateZoneToggles(
            @PathVariable String zoneId,
            @RequestParam(required = false) Boolean restaurantEnabled,
            @RequestParam(required = false) Boolean deliveryPartnerEnabled,
            @RequestParam(required = false) Boolean customerOrderingEnabled) {

        LocationZone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null) {
            return ResponseEntity.notFound().build();
        }

        if (restaurantEnabled != null) {
            zone.setRestaurantEnabled(restaurantEnabled);
        }
        if (deliveryPartnerEnabled != null) {
            zone.setDeliveryPartnerEnabled(deliveryPartnerEnabled);
        }
        if (customerOrderingEnabled != null) {
            zone.setCustomerOrderingEnabled(customerOrderingEnabled);
        }

        zoneRepository.saveAndFlush(zone);
        return ResponseEntity.ok(ApiResponse.success(mapToDto(zone)));
    }
    
    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityDto>>> getAllCities() {
        List<CityDto> dtos = cityRepository.findAll().stream().map(this::mapCityToDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }
    
    @PostMapping("/cities")
    @Transactional
    public ResponseEntity<ApiResponse<CityDto>> createCity(@RequestBody CityDto dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("cty-" + UUID.randomUUID().toString().substring(0, 6));
        }
        if (dto.getStatus() == null) {
            dto.setStatus("ACTIVE");
        }
        City entity = mapCityToEntity(dto);
        cityRepository.saveAndFlush(entity);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/unserviceable-requests")
    public ResponseEntity<ApiResponse<List<UnserviceableRequestDto>>> getUnserviceableRequests() {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(unserviceableRequests.values())));
    }

    @PostMapping("/unserviceable-requests")
    public ResponseEntity<ApiResponse<UnserviceableRequestDto>> createUnserviceableRequest(@RequestBody UnserviceableRequestDto dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("req-" + UUID.randomUUID().toString().substring(0, 6));
        }
        dto.setStatus("PENDING");
        dto.setCreatedAt(Instant.now());
        unserviceableRequests.put(dto.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PutMapping("/unserviceable-requests/{requestId}/status")
    public ResponseEntity<ApiResponse<UnserviceableRequestDto>> updateRequestStatus(
            @PathVariable String requestId,
            @RequestParam String status) {

        UnserviceableRequestDto req = unserviceableRequests.get(requestId);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        req.setStatus(status);
        unserviceableRequests.put(requestId, req);
        return ResponseEntity.ok(ApiResponse.success(req));
    }

    private LocationZoneDto mapToDto(LocationZone entity) {
        return new LocationZoneDto(
                entity.getId(),
                entity.getZoneName(),
                entity.getCityName(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getRadiusKm(),
                entity.getPolygonCoordinates(),
                entity.getActiveDrivers(),
                entity.getSurgeMultiplier(),
                entity.getStatus(),
                entity.isRestaurantEnabled(),
                entity.isDeliveryPartnerEnabled(),
                entity.isCustomerOrderingEnabled()
        );
    }

    private LocationZone mapToEntity(LocationZoneDto dto) {
        LocationZone entity = new LocationZone();
        entity.setId(dto.getId());
        entity.setZoneName(dto.getZoneName());
        entity.setCityName(dto.getCityName());
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setRadiusKm(dto.getRadiusKm());
        entity.setPolygonCoordinates(dto.getPolygonCoordinates());
        entity.setActiveDrivers(dto.getActiveDrivers());
        entity.setSurgeMultiplier(dto.getSurgeMultiplier());
        entity.setStatus(dto.getStatus());
        entity.setRestaurantEnabled(dto.isRestaurantEnabled());
        entity.setDeliveryPartnerEnabled(dto.isDeliveryPartnerEnabled());
        entity.setCustomerOrderingEnabled(dto.isCustomerOrderingEnabled());
        return entity;
    }

    private CityDto mapCityToDto(City entity) {
        CityDto dto = new CityDto();
        dto.setId(entity.getId());
        dto.setCityName(entity.getCityName());
        dto.setState(entity.getState());
        dto.setActiveZonesCount(entity.getActiveZonesCount());
        dto.setActiveMerchantsCount(entity.getActiveMerchantsCount());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    private City mapCityToEntity(CityDto dto) {
        City entity = new City();
        entity.setId(dto.getId());
        entity.setCityName(dto.getCityName());
        entity.setState(dto.getState());
        entity.setActiveZonesCount(dto.getActiveZonesCount());
        entity.setActiveMerchantsCount(dto.getActiveMerchantsCount());
        entity.setStatus(dto.getStatus());
        return entity;
    }
}
