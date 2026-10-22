package com.foodie.admin.controller;

import com.foodie.admin.dto.CityDto;
import com.foodie.admin.dto.DeliveryChargesDto;
import com.foodie.admin.dto.LocationZoneDto;
import com.foodie.admin.dto.RadiusSettingsDto;
import com.foodie.admin.dto.ServiceAreaDto;
import com.foodie.admin.dto.UnserviceableRequestDto;
import com.foodie.admin.entity.City;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.CityRepository;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.restaurant.repository.RestaurantAddressRepository;
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

    @Autowired(required = false)
    private AdminUserRepository adminUserRepository;

    @Autowired(required = false)
    private com.foodie.admin.service.AdminService adminService;

    @Autowired(required = false)
    private RestaurantAddressRepository restaurantAddressRepository;

    @Autowired(required = false)
    private com.foodie.admin.security.AdminAccess adminAccess;

    private final ConcurrentHashMap<String, UnserviceableRequestDto> unserviceableRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ServiceAreaDto> serviceAreas = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, DeliveryChargesDto> zoneDeliveryCharges = new ConcurrentHashMap<>();

    private DeliveryChargesDto deliveryCharges = new DeliveryChargesDto(
            new BigDecimal("35.00"),
            new BigDecimal("3.00"),
            new BigDecimal("10.00"),
            new BigDecimal("499.00"),
            new BigDecimal("25.00"),
            new BigDecimal("1.15")
    );

    public static String resolveZoneKey(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return "DEFAULT";
        String clean = zoneId.trim().toLowerCase();
        if (clean.equals("all")) return "DEFAULT";
        if (clean.contains("tumkur") || clean.contains("tumakuru") || clean.contains("tmk")) return "dz-tumkur";
        if (clean.contains("bangalore") || clean.contains("bengaluru") || clean.contains("blr")) return "dz-bangalore";
        return clean;
    }

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

            City c4 = new City();
            c4.setId("cty-tumkur");
            c4.setCityName("Tumakuru");
            c4.setState("Karnataka");
            c4.setActiveZonesCount(1);
            c4.setActiveMerchantsCount(48);
            c4.setStatus("ACTIVE");
            cityRepository.saveAndFlush(c4);
        }

        if (!cityRepository.existsById("cty-tumkur") && cityRepository.findFirstCityByCityNameIgnoreCase("Tumakuru").isEmpty() && cityRepository.findFirstCityByCityNameIgnoreCase("Tumkur").isEmpty()) {
            City cTmk = new City();
            cTmk.setId("cty-tumkur");
            cTmk.setCityName("Tumakuru");
            cTmk.setState("Karnataka");
            cTmk.setActiveZonesCount(1);
            cTmk.setActiveMerchantsCount(48);
            cTmk.setStatus("ACTIVE");
            cityRepository.saveAndFlush(cTmk);
        }

        // Only seed canonical default zones if no zones exist in the database at all (initial fresh install)
        if (zoneRepository.count() == 0 && cityRepository.count() <= 4) {
            LocationZone bgl = new LocationZone();
            bgl.setId("dz-bangalore");
            bgl.setZoneName("Bangalore Zone");
            bgl.setCityName("Bangalore");
            bgl.setLatitude(12.9716);
            bgl.setLongitude(77.5946);
            bgl.setRadiusKm(25.0);
            bgl.setPolygonCoordinates("12.9716,77.5946 | 13.0000,77.6200 | 12.9500,77.5800");
            bgl.setActiveDrivers(45);
            bgl.setSurgeMultiplier(new BigDecimal("1.00"));
            bgl.setStatus("ACTIVE");
            bgl.setRestaurantEnabled(true);
            bgl.setDeliveryPartnerEnabled(true);
            bgl.setCustomerOrderingEnabled(true);
            zoneRepository.saveAndFlush(bgl);

            LocationZone tmk = new LocationZone();
            tmk.setId("dz-tumkur");
            tmk.setZoneName("Tumkur Zone");
            tmk.setCityName("Tumkur");
            tmk.setLatitude(13.3379);
            tmk.setLongitude(77.1173);
            tmk.setRadiusKm(20.0);
            tmk.setPolygonCoordinates("13.3379,77.1173 | 13.3500,77.1300 | 13.3200,77.1000");
            tmk.setActiveDrivers(22);
            tmk.setSurgeMultiplier(new BigDecimal("1.00"));
            tmk.setStatus("ACTIVE");
            tmk.setRestaurantEnabled(true);
            tmk.setDeliveryPartnerEnabled(true);
            tmk.setCustomerOrderingEnabled(true);
            zoneRepository.saveAndFlush(tmk);
        }

        // Deactivate duplicate legacy entries ("zone 1") without deleting historical data
        List<LocationZone> all = zoneRepository.findAll();
        for (LocationZone z : all) {
            if (z.getZoneName() != null && z.getZoneName().toLowerCase().contains("zone 1")) {
                if ("ACTIVE".equalsIgnoreCase(z.getStatus())) {
                    z.setStatus("INACTIVE");
                    zoneRepository.saveAndFlush(z);
                }
            }
        }

        if (serviceAreas.isEmpty()) {
            serviceAreas.put("sa-1", new ServiceAreaDto("sa-1", "Indiranagar & Domlur", "Bangalore", "560038", "FULL_COVERAGE", 120));
            serviceAreas.put("sa-2", new ServiceAreaDto("sa-2", "Koramangala 4th Block", "Bangalore", "560034", "FULL_COVERAGE", 145));
            serviceAreas.put("sa-3", new ServiceAreaDto("sa-3", "MG Road & Ashok Nagar", "Tumkur", "572101", "FULL_COVERAGE", 65));
            serviceAreas.put("sa-4", new ServiceAreaDto("sa-4", "Siddaganga Ext & Batwadi", "Tumkur", "572102", "FULL_COVERAGE", 48));
        }

        if (unserviceableRequests.isEmpty()) {
            unserviceableRequests.put("req-1", new UnserviceableRequestDto("req-1", "Kengeri Biryani Hub", "Suresh Kumar", "suresh@kengeribiryani.com", "+919876543210", "Kengeri Satellite Town", "Bangalore", 12.9177, 77.4838, "PENDING", Instant.now().minusSeconds(86400)));
            unserviceableRequests.put("req-2", new UnserviceableRequestDto("req-2", "Pavithra Thatte Idli", "Manjunath T", "manju@pavithraidli.com", "+919876543211", "Kyatsandra Main Road", "Tumkur", 13.3150, 77.1650, "PENDING", Instant.now().minusSeconds(43200)));
        }
    }

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<LocationZoneDto>>> getAllZones(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String requestedZoneId) {
        String authorizedZone = null;
        if (adminAccess != null && authentication != null && authentication.isAuthenticated()) {
            try {
                authorizedZone = adminAccess.resolveAuthorizedZone(authentication, requestedZoneId);
            } catch (Exception e) {
                // Fallback for public or unauthenticated requests
            }
        }
        final String effectiveZone = authorizedZone;
        List<LocationZoneDto> dtos = zoneRepository.findAll().stream()
                .filter(z -> !"DELETED".equalsIgnoreCase(z.getStatus()))
                .filter(z -> z.getZoneName() == null || !z.getZoneName().toLowerCase().contains("zone 1"))
                .filter(z -> {
                    if (effectiveZone == null || effectiveZone.isBlank() || effectiveZone.equalsIgnoreCase("ALL")) {
                        return true;
                    }
                    String zId = z.getId() != null ? z.getId() : "";
                    String zName = z.getZoneName() != null ? z.getZoneName() : "";
                    String zCity = z.getCityName() != null ? z.getCityName() : "";
                    return com.foodie.admin.security.AdminAccess.areZonesEquivalent(zId, effectiveZone) ||
                           com.foodie.admin.security.AdminAccess.areZonesEquivalent(zName, effectiveZone) ||
                           com.foodie.admin.security.AdminAccess.areZonesEquivalent(zCity, effectiveZone);
                })
                .map(this::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PostMapping("/zones")
    @Transactional
    public ResponseEntity<ApiResponse<LocationZoneDto>> createZone(
            @RequestBody LocationZoneDto dto,
            org.springframework.security.core.Authentication authentication) {
        if (adminAccess != null && authentication != null && authentication.isAuthenticated()) {
            com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
            if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN) {
                throw new com.foodie.common.exception.ForbiddenException("Zone creation is strictly reserved for Super Administrators.");
            }
        }

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
            cityRepository.findFirstCityByCityNameIgnoreCase(dto.getCityName()).ifPresent(city -> {
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
            org.springframework.security.core.Authentication authentication,
            @RequestParam(required = false) Boolean restaurantEnabled,
            @RequestParam(required = false) Boolean deliveryPartnerEnabled,
            @RequestParam(required = false) Boolean customerOrderingEnabled) {

        if (adminAccess != null && authentication != null && authentication.isAuthenticated()) {
            if (!adminAccess.canAccessZone(authentication, zoneId)) {
                throw new com.foodie.common.exception.ForbiddenException("Cannot modify switches for a foreign zone.");
            }
        }

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
    public ResponseEntity<ApiResponse<Boolean>> deleteZone(
            @PathVariable String zoneId,
            org.springframework.security.core.Authentication authentication) {
        if (adminAccess != null && authentication != null && authentication.isAuthenticated()) {
            com.foodie.admin.entity.AdminUser admin = adminAccess.requireAdmin(authentication);
            if (admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN) {
                throw new com.foodie.common.exception.ForbiddenException("Zone deletion is strictly reserved for Super Administrators.");
            }
        }

        String zIdLower = zoneId.trim().toLowerCase();
        List<LocationZone> toDelete = zoneRepository.findAll().stream()
                .filter(z -> z.getId().equalsIgnoreCase(zoneId) ||
                        (z.getZoneName() != null && z.getZoneName().equalsIgnoreCase(zoneId)) ||
                        (zIdLower.contains("bengalu") && z.getId().equalsIgnoreCase("dz-bangalore")) ||
                        (zIdLower.equalsIgnoreCase("dz-bangalore") && (z.getZoneName() != null && z.getZoneName().toLowerCase().contains("bangalore"))) ||
                        (zIdLower.equalsIgnoreCase("dz-tumkur") && (z.getZoneName() != null && z.getZoneName().toLowerCase().contains("tumkur"))))
                .collect(Collectors.toList());

        for (LocationZone zone : toDelete) {
            zoneRepository.delete(zone);
            if (zone.getCityName() != null) {
                cityRepository.findFirstCityByCityNameIgnoreCase(zone.getCityName()).ifPresent(city -> {
                    long remaining = zoneRepository.findAll().stream()
                            .filter(z -> !z.getId().equalsIgnoreCase(zone.getId()) &&
                                    "ACTIVE".equalsIgnoreCase(z.getStatus()) &&
                                    z.getCityName() != null &&
                                    z.getCityName().equalsIgnoreCase(city.getCityName()))
                            .count();
                    city.setActiveZonesCount((int) remaining);
                    cityRepository.saveAndFlush(city);
                });
            }
        }

        // Delete user credentials permanently associated with this zone
        if (adminService != null) {
            adminService.deleteAdminUsersByZoneId(zoneId);
            adminService.deleteAdminUsersByZoneId(zIdLower);
            for (LocationZone zone : toDelete) {
                if (zone.getId() != null) adminService.deleteAdminUsersByZoneId(zone.getId());
                if (zone.getZoneName() != null) adminService.deleteAdminUsersByZoneId(zone.getZoneName());
            }
        } else if (adminUserRepository != null) {
            List<AdminUser> usersToDelete = adminUserRepository.findAll().stream()
                    .filter(u -> {
                        if (u.getAssignedZoneId() == null) return false;
                        String uZone = u.getAssignedZoneId().trim().toLowerCase();
                        return uZone.equalsIgnoreCase(zIdLower) ||
                                uZone.contains(zIdLower) ||
                                zIdLower.contains(uZone);
                    })
                    .collect(Collectors.toList());
            if (!usersToDelete.isEmpty()) {
                adminUserRepository.deleteAll(usersToDelete);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityDto>>> getAllCities() {
        List<LocationZone> allZones = zoneRepository.findAll();
        List<CityDto> dtos = cityRepository.findAll().stream().map(city -> {
            CityDto dto = mapCityToDto(city);
            String cityName = dto.getCityName();
            if (cityName != null) {
                String cLower = cityName.trim().toLowerCase();
                // Dynamically count ACTIVE non-deleted zones belonging to this city
                long activeZones = allZones.stream().filter(z -> {
                    if (!"ACTIVE".equalsIgnoreCase(z.getStatus())) return false;
                    String zCity = z.getCityName() != null ? z.getCityName().trim().toLowerCase() : "";
                    if (zCity.equals(cLower)) return true;
                    if ((cLower.contains("tumkur") || cLower.contains("tumakuru")) &&
                        (zCity.contains("tumkur") || zCity.contains("tumakuru"))) return true;
                    if ((cLower.contains("bangalore") || cLower.contains("bengalu")) &&
                        (zCity.contains("bangalore") || zCity.contains("bengalu"))) return true;
                    return false;
                }).count();
                dto.setActiveZonesCount((int) activeZones);

                // Dynamically count active merchants for this city
                int merchantsCount = 0;
                if (restaurantAddressRepository != null) {
                    merchantsCount = (int) restaurantAddressRepository.findAll().stream().filter(addr -> {
                        String aCity = addr.getCity() != null ? addr.getCity().trim().toLowerCase() : "";
                        if (aCity.equals(cLower)) return true;
                        if ((cLower.contains("tumkur") || cLower.contains("tumakuru")) &&
                            (aCity.contains("tumkur") || aCity.contains("tumakuru"))) return true;
                        if ((cLower.contains("bangalore") || cLower.contains("bengalu")) &&
                            (aCity.contains("bangalore") || aCity.contains("bengalu"))) return true;
                        return false;
                    }).count();
                }
                if (merchantsCount == 0 && city.getActiveMerchantsCount() > 0) {
                    merchantsCount = city.getActiveMerchantsCount();
                }
                if (merchantsCount == 0) {
                    if (cLower.contains("tumkur") || cLower.contains("tumakuru")) merchantsCount = 48;
                    else if (cLower.contains("bangalore") || cLower.contains("bengalu")) merchantsCount = 265;
                    else if (cLower.contains("mumbai")) merchantsCount = 190;
                    else if (cLower.contains("delhi")) merchantsCount = 145;
                    else merchantsCount = 12;
                }
                dto.setActiveMerchantsCount(merchantsCount);
                if (city.getActiveMerchantsCount() != merchantsCount || city.getActiveZonesCount() != (int) activeZones) {
                    city.setActiveMerchantsCount(merchantsCount);
                    city.setActiveZonesCount((int) activeZones);
                    cityRepository.saveAndFlush(city);
                }
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
        String lowerId = cityId.toLowerCase().trim();
        List<City> cities = cityRepository.findAll();
        for (City c : cities) {
            String cId = c.getId() != null ? c.getId().toLowerCase().trim() : "";
            String cName = c.getCityName() != null ? c.getCityName().toLowerCase().trim() : "";
            if (cId.equals(lowerId) || cName.equals(lowerId) ||
                (lowerId.contains("bangalore") && (cName.contains("bangalore") || cName.contains("bengalu"))) ||
                (lowerId.contains("tumkur") && (cName.contains("tumkur") || cName.contains("tumakuru")))) {
                cityRepository.delete(c);
            }
        }
        if (cityRepository.existsById(cityId)) {
            cityRepository.deleteById(cityId);
        }
        cityRepository.flush();
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/unserviceable-requests")
    public ResponseEntity<ApiResponse<List<UnserviceableRequestDto>>> getUnserviceableRequests(
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        String activeZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        List<UnserviceableRequestDto> list = new ArrayList<>(unserviceableRequests.values());
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
            String lower = activeZoneId.toLowerCase();
            if (lower.contains("tumkur")) {
                list = list.stream().filter(r -> r.getCityName() != null && r.getCityName().equalsIgnoreCase("Tumkur")).collect(Collectors.toList());
            } else if (lower.contains("bangalore") || lower.contains("bengaluru")) {
                list = list.stream().filter(r -> r.getCityName() != null && (r.getCityName().equalsIgnoreCase("Bangalore") || r.getCityName().equalsIgnoreCase("Bengaluru"))).collect(Collectors.toList());
            }
        }
        return ResponseEntity.ok(ApiResponse.success(list));
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
    public ResponseEntity<ApiResponse<List<ServiceAreaDto>>> getServiceAreas(
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        String activeZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);

        List<ServiceAreaDto> list = new ArrayList<>(serviceAreas.values());
        if (activeZoneId != null && !activeZoneId.equalsIgnoreCase("ALL")) {
            String lower = activeZoneId.toLowerCase();
            if (lower.contains("tumkur")) {
                list = list.stream().filter(s -> s.getCityName() != null && s.getCityName().equalsIgnoreCase("Tumkur")).collect(Collectors.toList());
            } else if (lower.contains("bangalore") || lower.contains("bengaluru")) {
                list = list.stream().filter(s -> s.getCityName() != null && (s.getCityName().equalsIgnoreCase("Bangalore") || s.getCityName().equalsIgnoreCase("Bengaluru"))).collect(Collectors.toList());
            }
        }
        return ResponseEntity.ok(ApiResponse.success(list));
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
    public ResponseEntity<ApiResponse<DeliveryChargesDto>> getDeliveryCharges(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);
        String activeZoneId = null;
        try {
            if (adminAccess != null && authentication != null) {
                activeZoneId = adminAccess.resolveAuthorizedZone(authentication, requestedZoneId);
            } else {
                activeZoneId = requestedZoneId;
            }
        } catch (Exception e) {
            activeZoneId = requestedZoneId;
        }

        String key = resolveZoneKey(activeZoneId);
        DeliveryChargesDto dto = zoneDeliveryCharges.get(key);
        if (dto == null) {
            dto = zoneDeliveryCharges.getOrDefault("DEFAULT", deliveryCharges);
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PutMapping("/delivery-charges")
    public ResponseEntity<ApiResponse<DeliveryChargesDto>> updateDeliveryCharges(
            org.springframework.security.core.Authentication authentication,
            @RequestHeader(value = "X-Active-Zone-Id", required = false) String activeZoneHeader,
            @RequestParam(value = "zoneId", required = false) String zoneIdParam,
            @RequestBody DeliveryChargesDto dto) {
        String requestedZoneId = (activeZoneHeader != null && !activeZoneHeader.isBlank())
                ? activeZoneHeader.trim()
                : (zoneIdParam != null && !zoneIdParam.isBlank() ? zoneIdParam.trim() : null);
        String activeZoneId = null;
        try {
            if (adminAccess != null && authentication != null) {
                activeZoneId = adminAccess.resolveAuthorizedZone(authentication, requestedZoneId);
            } else {
                activeZoneId = requestedZoneId;
            }
        } catch (Exception e) {
            activeZoneId = requestedZoneId;
        }

        String key = resolveZoneKey(activeZoneId);
        zoneDeliveryCharges.put(key, dto);
        if ("DEFAULT".equals(key)) {
            this.deliveryCharges = dto;
        }
        return ResponseEntity.ok(ApiResponse.success(dto));
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
