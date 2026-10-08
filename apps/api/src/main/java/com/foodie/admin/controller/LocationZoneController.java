package com.foodie.admin.controller;

import com.foodie.admin.dto.CityDto;
import com.foodie.admin.dto.DeliveryChargesDto;
import com.foodie.admin.dto.LocationZoneDto;
import com.foodie.admin.dto.RadiusSettingsDto;
import com.foodie.admin.dto.ServiceAreaDto;
import com.foodie.admin.dto.UnserviceableRequestDto;
import com.foodie.admin.entity.City;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.CityRepository;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final ConcurrentHashMap<String, ServiceAreaDto> serviceAreas = new ConcurrentHashMap<>();

    private DeliveryChargesDto deliveryCharges = new DeliveryChargesDto(
            new BigDecimal("35.00"),
            new BigDecimal("3.00"),
            new BigDecimal("10.00"),
            new BigDecimal("499.00"),
            new BigDecimal("25.00"),
            new BigDecimal("1.15")
    );

    private RadiusSettingsDto radiusSettings = new RadiusSettingsDto(
            new BigDecimal("15.00"),
            new BigDecimal("10.00"),
            new BigDecimal("5.00"),
            "GPS_ROAD"
    );

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedInitialLocationDataIfEmpty() {
        if (cityRepository.count() == 0) {
            City c1 = new City();
            c1.setId("cty-bangalore");
            c1.setCityName("Bangalore");
            c1.setState("Karnataka");
            c1.setActiveZonesCount(2);
            c1.setActiveMerchantsCount(265);
            c1.setStatus("ACTIVE");
            cityRepository.saveAndFlush(c1);

            City c2 = new City();
            c2.setId("cty-mumbai");
            c2.setCityName("Mumbai");
            c2.setState("Maharashtra");
            c2.setActiveZonesCount(1);
            c2.setActiveMerchantsCount(190);
            c2.setStatus("ACTIVE");
            cityRepository.saveAndFlush(c2);

            City c3 = new City();
            c3.setId("cty-delhi");
            c3.setCityName("Delhi-NCR");
            c3.setState("Delhi");
            c3.setActiveZonesCount(1);
            c3.setActiveMerchantsCount(145);
            c3.setStatus("ACTIVE");
            cityRepository.saveAndFlush(c3);
        }

        if (zoneRepository.count() == 0) {
            LocationZone z1 = new LocationZone();
            z1.setId("dz-tumkur");
            z1.setZoneName("Tumkur Central");
            z1.setCityName("Bangalore");
            z1.setLatitude(13.3379);
            z1.setLongitude(77.1173);
            z1.setRadiusKm(15.0);
            z1.setPolygonCoordinates("13.3379,77.1173 | 13.3450,77.1250 | 13.3300,77.1300");
            z1.setActiveDrivers(18);
            z1.setSurgeMultiplier(new BigDecimal("1.00"));
            z1.setStatus("ACTIVE");
            z1.setRestaurantEnabled(true);
            z1.setDeliveryPartnerEnabled(true);
            z1.setCustomerOrderingEnabled(true);
            zoneRepository.saveAndFlush(z1);

            LocationZone z2 = new LocationZone();
            z2.setId("dz-indiranagar");
            z2.setZoneName("Indiranagar Tech Corridor");
            z2.setCityName("Bangalore");
            z2.setLatitude(12.9784);
            z2.setLongitude(77.6408);
            z2.setRadiusKm(8.0);
            z2.setPolygonCoordinates("12.9784,77.6408 | 12.9850,77.6480 | 12.9700,77.6520");
            z2.setActiveDrivers(32);
            z2.setSurgeMultiplier(new BigDecimal("1.15"));
            z2.setStatus("ACTIVE");
            z2.setRestaurantEnabled(true);
            z2.setDeliveryPartnerEnabled(true);
            z2.setCustomerOrderingEnabled(true);
            zoneRepository.saveAndFlush(z2);
        }

        if (serviceAreas.isEmpty()) {
            serviceAreas.put("sa-1", new ServiceAreaDto("sa-1", "Indiranagar & Domlur", "Bangalore", "560038", "FULL_COVERAGE", 120));
            serviceAreas.put("sa-2", new ServiceAreaDto("sa-2", "Koramangala 4th Block", "Bangalore", "560034", "FULL_COVERAGE", 145));
            serviceAreas.put("sa-3", new ServiceAreaDto("sa-3", "Bandra West & Khar", "Mumbai", "400050", "FULL_COVERAGE", 190));
            serviceAreas.put("sa-4", new ServiceAreaDto("sa-4", "Connaught Place & Janpath", "Delhi-NCR", "110001", "PARTIAL_COVERAGE", 85));
        }
    }

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

        if (dto.getCityName() != null) {
            cityRepository.findByCityNameIgnoreCase(dto.getCityName()).ifPresent(city -> {
                city.setActiveZonesCount(city.getActiveZonesCount() + 1);
                cityRepository.saveAndFlush(city);
            });
        }

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

    @PatchMapping("/zones/{zoneId}/status")
    @Transactional
    public ResponseEntity<ApiResponse<LocationZoneDto>> updateZoneStatus(
            @PathVariable String zoneId,
            @RequestParam String status) {
        LocationZone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null)
            return ResponseEntity.notFound().build();
        zone.setStatus(status);
        zoneRepository.saveAndFlush(zone);
        return ResponseEntity.ok(ApiResponse.success(mapToDto(zone)));
    }

    @DeleteMapping("/zones/{zoneId}")
    @Transactional
    public ResponseEntity<ApiResponse<Boolean>> deleteZone(@PathVariable String zoneId) {
        LocationZone zone = zoneRepository.findById(zoneId).orElse(null);
        if (zone == null)
            return ResponseEntity.notFound().build();

        zoneRepository.deleteById(zoneId);
        if (zone.getCityName() != null) {
            cityRepository.findByCityNameIgnoreCase(zone.getCityName()).ifPresent(city -> {
                if (city.getActiveZonesCount() > 0) {
                    city.setActiveZonesCount(city.getActiveZonesCount() - 1);
                    cityRepository.saveAndFlush(city);
                }
            });
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityDto>>> getAllCities() {
        List<CityDto> dtos = cityRepository.findAll().stream().map(city -> {
            CityDto dto = mapCityToDto(city);
            if (dto.getCityName() != null) {
                dto.setActiveZonesCount(zoneRepository.countByCityNameIgnoreCase(dto.getCityName()));
            }
            return dto;
        }).collect(Collectors.toList());
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

    @PatchMapping("/cities/{cityId}/status")
    @Transactional
    public ResponseEntity<ApiResponse<CityDto>> updateCityStatus(
            @PathVariable String cityId,
            @RequestParam String status) {
        City city = cityRepository.findById(cityId).orElse(null);
        if (city == null)
            return ResponseEntity.notFound().build();
        city.setStatus(status);
        cityRepository.saveAndFlush(city);
        return ResponseEntity.ok(ApiResponse.success(mapCityToDto(city)));
    }

    @DeleteMapping("/cities/{cityId}")
    @Transactional
    public ResponseEntity<ApiResponse<Boolean>> deleteCity(@PathVariable String cityId) {
        if (!cityRepository.existsById(cityId))
            return ResponseEntity.notFound().build();
        cityRepository.deleteById(cityId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/unserviceable-requests")
    public ResponseEntity<ApiResponse<List<UnserviceableRequestDto>>> getUnserviceableRequests() {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(unserviceableRequests.values())));
    }

    @PostMapping("/unserviceable-requests")
    public ResponseEntity<ApiResponse<UnserviceableRequestDto>> createUnserviceableRequest(
            @RequestBody UnserviceableRequestDto dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("req-" + UUID.randomUUID().toString().substring(0, 6));
        }
        dto.setStatus("PENDING");
        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(Instant.now());
        }
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

    @PostMapping("/unserviceable-requests/{requestId}/approve")
    @Transactional
    public ResponseEntity<ApiResponse<LocationZoneDto>> approveUnserviceableRequest(
            @PathVariable String requestId) {
        UnserviceableRequestDto req = unserviceableRequests.get(requestId);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        req.setStatus("APPROVED");
        unserviceableRequests.put(requestId, req);

        LocationZoneDto newZone = new LocationZoneDto();
        newZone.setId("dz-" + UUID.randomUUID().toString().substring(0, 6));
        newZone.setZoneName(req.getRestaurantName() + " Dedicated Zone");
        newZone.setCityName(req.getCityName() != null ? req.getCityName() : "Bangalore");
        newZone.setLatitude(req.getLatitude() != null ? req.getLatitude() : 12.9716);
        newZone.setLongitude(req.getLongitude() != null ? req.getLongitude() : 77.5946);
        newZone.setRadiusKm(4.0);
        newZone.setPolygonCoordinates(String.format("%.4f,%.4f | %.4f,%.4f | %.4f,%.4f",
                newZone.getLatitude(), newZone.getLongitude(),
                newZone.getLatitude() + 0.01, newZone.getLongitude() + 0.01,
                newZone.getLatitude() - 0.01, newZone.getLongitude() - 0.01));
        newZone.setStatus("ACTIVE");
        newZone.setSurgeMultiplier(new BigDecimal("1.00"));
        newZone.setRestaurantEnabled(true);
        newZone.setDeliveryPartnerEnabled(true);
        newZone.setCustomerOrderingEnabled(true);

        LocationZone entity = mapToEntity(newZone);
        zoneRepository.saveAndFlush(entity);

        return ResponseEntity.ok(ApiResponse.success(newZone));
    }

    @GetMapping("/service-areas")
    public ResponseEntity<ApiResponse<List<ServiceAreaDto>>> getServiceAreas() {
        return ResponseEntity.ok(ApiResponse.success(new ArrayList<>(serviceAreas.values())));
    }

    @PostMapping("/service-areas")
    public ResponseEntity<ApiResponse<ServiceAreaDto>> createServiceArea(@RequestBody ServiceAreaDto dto) {
        if (dto.getId() == null || dto.getId().isBlank()) {
            dto.setId("sa-" + UUID.randomUUID().toString().substring(0, 6));
        }
        if (dto.getCoverageStatus() == null) {
            dto.setCoverageStatus("FULL_COVERAGE");
        }
        if (dto.getTotalOutlets() == null) {
            dto.setTotalOutlets(0);
        }
        serviceAreas.put(dto.getId(), dto);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @DeleteMapping("/service-areas/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteServiceArea(@PathVariable String id) {
        if (!serviceAreas.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }
        serviceAreas.remove(id);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/delivery-charges")
    public ResponseEntity<ApiResponse<DeliveryChargesDto>> getDeliveryCharges() {
        return ResponseEntity.ok(ApiResponse.success(deliveryCharges));
    }

    @PutMapping("/delivery-charges")
    public ResponseEntity<ApiResponse<DeliveryChargesDto>> updateDeliveryCharges(@RequestBody DeliveryChargesDto dto) {
        this.deliveryCharges = dto;
        return ResponseEntity.ok(ApiResponse.success(this.deliveryCharges));
    }

    @GetMapping("/radius-settings")
    public ResponseEntity<ApiResponse<RadiusSettingsDto>> getRadiusSettings() {
        return ResponseEntity.ok(ApiResponse.success(radiusSettings));
    }

    @PutMapping("/radius-settings")
    public ResponseEntity<ApiResponse<RadiusSettingsDto>> updateRadiusSettings(@RequestBody RadiusSettingsDto dto) {
        this.radiusSettings = dto;
        return ResponseEntity.ok(ApiResponse.success(this.radiusSettings));
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
                entity.isCustomerOrderingEnabled());
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
