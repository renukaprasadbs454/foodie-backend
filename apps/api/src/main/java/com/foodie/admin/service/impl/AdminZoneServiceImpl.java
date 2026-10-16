package com.foodie.admin.service.impl;

import com.foodie.admin.dto.ActiveOperatingZoneDto;
import com.foodie.admin.dto.AdminZoneLoginHistoryDto;
import com.foodie.admin.dto.ZoneSelectionResponseDto;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.AdminZoneLoginHistory;
import com.foodie.admin.entity.LocationZone;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.AdminZoneLoginHistoryRepository;
import com.foodie.admin.repository.LocationZoneRepository;
import com.foodie.admin.service.AdminService;
import com.foodie.admin.service.AdminZoneService;
import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.common.exception.ForbiddenException;
import com.foodie.common.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminZoneServiceImpl implements AdminZoneService {

    private static final Logger log = LoggerFactory.getLogger(AdminZoneServiceImpl.class);

    private final LocationZoneRepository locationZoneRepository;
    private final AdminZoneLoginHistoryRepository loginHistoryRepository;
    private final AdminUserRepository adminUserRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final AdminService adminService;

    public AdminZoneServiceImpl(
            LocationZoneRepository locationZoneRepository,
            AdminZoneLoginHistoryRepository loginHistoryRepository,
            AdminUserRepository adminUserRepository,
            UserCredentialRepository userCredentialRepository,
            AdminService adminService
    ) {
        this.locationZoneRepository = locationZoneRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.adminUserRepository = adminUserRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.adminService = adminService;
    }

    public static final String BANGALORE_ZONE_ID = "dz-bangalore";
    public static final String BANGALORE_ZONE_NAME = "Bangalore Zone";
    public static final String BANGALORE_CITY = "Bangalore";

    public static final String TUMKUR_ZONE_ID = "dz-tumkur";
    public static final String TUMKUR_ZONE_NAME = "Tumkur Zone";
    public static final String TUMKUR_CITY = "Tumkur";

    @Override
    @Transactional(readOnly = true)
    public List<ActiveOperatingZoneDto> getAvailableZones(UUID userCredentialId) {
        List<LocationZone> allZones = locationZoneRepository.findAll();

        // 1. Locate canonical or best matching record for Bangalore Zone
        LocationZone bgl = allZones.stream()
                .filter(z -> BANGALORE_ZONE_ID.equalsIgnoreCase(z.getId())
                        || BANGALORE_ZONE_NAME.equalsIgnoreCase(z.getZoneName()))
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getStatus()))
                .findFirst()
                .orElse(null);

        if (bgl == null) {
            bgl = locationZoneRepository.findById(BANGALORE_ZONE_ID).orElseGet(this::createDefaultBangaloreZone);
        }

        // 2. Locate canonical or best matching record for Tumkur Zone
        LocationZone tmk = allZones.stream()
                .filter(z -> TUMKUR_ZONE_ID.equalsIgnoreCase(z.getId())
                        || TUMKUR_ZONE_NAME.equalsIgnoreCase(z.getZoneName()))
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getStatus()))
                .findFirst()
                .orElse(null);

        if (tmk == null) {
            tmk = locationZoneRepository.findById(TUMKUR_ZONE_ID).orElseGet(this::createDefaultTumkurZone);
        }

        // Map strictly to the two approved operating zones:
        // 1. Bangalore Zone — Bangalore
        // 2. Tumkur Zone — Tumkur
        List<ActiveOperatingZoneDto> result = new java.util.ArrayList<>(2);
        result.add(new ActiveOperatingZoneDto(
                bgl.getId() != null ? bgl.getId() : BANGALORE_ZONE_ID,
                BANGALORE_ZONE_NAME,
                BANGALORE_CITY,
                bgl.getLatitude() != null ? bgl.getLatitude() : 12.9716,
                bgl.getLongitude() != null ? bgl.getLongitude() : 77.5946,
                bgl.getRadiusKm() != null ? bgl.getRadiusKm() : 25.0,
                "ACTIVE",
                bgl.isRestaurantEnabled(),
                bgl.isDeliveryPartnerEnabled(),
                bgl.isCustomerOrderingEnabled(),
                bgl.getActiveDrivers() > 0 ? bgl.getActiveDrivers() : 45
        ));

        result.add(new ActiveOperatingZoneDto(
                tmk.getId() != null ? tmk.getId() : TUMKUR_ZONE_ID,
                TUMKUR_ZONE_NAME,
                TUMKUR_CITY,
                tmk.getLatitude() != null ? tmk.getLatitude() : 13.3379,
                tmk.getLongitude() != null ? tmk.getLongitude() : 77.1173,
                tmk.getRadiusKm() != null ? tmk.getRadiusKm() : 20.0,
                "ACTIVE",
                tmk.isRestaurantEnabled(),
                tmk.isDeliveryPartnerEnabled(),
                tmk.isCustomerOrderingEnabled(),
                tmk.getActiveDrivers() > 0 ? tmk.getActiveDrivers() : 22
        ));

        // Dynamic active zones created by Super Admin
        for (LocationZone z : allZones) {
            if (z.getId() == null) continue;
            if (BANGALORE_ZONE_ID.equalsIgnoreCase(z.getId()) || TUMKUR_ZONE_ID.equalsIgnoreCase(z.getId())) {
                continue;
            }
            if ("ACTIVE".equalsIgnoreCase(z.getStatus())) {
                result.add(new ActiveOperatingZoneDto(
                        z.getId(),
                        z.getZoneName() != null ? z.getZoneName() : z.getId(),
                        z.getCityName() != null ? z.getCityName() : "Karnataka",
                        z.getLatitude() != null ? z.getLatitude() : 12.9716,
                        z.getLongitude() != null ? z.getLongitude() : 77.5946,
                        z.getRadiusKm() != null ? z.getRadiusKm() : 15.0,
                        "ACTIVE",
                        z.isRestaurantEnabled(),
                        z.isDeliveryPartnerEnabled(),
                        z.isCustomerOrderingEnabled(),
                        z.getActiveDrivers() > 0 ? z.getActiveDrivers() : 10
                ));
            }
        }

        // Keep alphabetical order: Bangalore Zone, then Tumkur Zone, then newly created
        result.sort(Comparator.comparing(ActiveOperatingZoneDto::zoneName, String.CASE_INSENSITIVE_ORDER));

        if (userCredentialId != null) {
            AdminUser admin = adminUserRepository.findByUserCredentialId(userCredentialId)
                    .or(() -> adminUserRepository.findById(userCredentialId))
                    .orElse(null);
            if (admin != null && admin.getRole() != null
                    && admin.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN) {
                if (admin.getAssignedZoneId() != null && !admin.getAssignedZoneId().isBlank()) {
                    String assigned = admin.getAssignedZoneId().trim();
                    return result.stream()
                            .filter(z -> assigned.equalsIgnoreCase(z.id()) || assigned.equalsIgnoreCase(z.zoneName()))
                            .toList();
                }
            }
        }

        return result;
    }

    private LocationZone createDefaultBangaloreZone() {
        LocationZone z = new LocationZone();
        z.setId(BANGALORE_ZONE_ID);
        z.setZoneName(BANGALORE_ZONE_NAME);
        z.setCityName(BANGALORE_CITY);
        z.setLatitude(12.9716);
        z.setLongitude(77.5946);
        z.setRadiusKm(25.0);
        z.setPolygonCoordinates("12.9716,77.5946 | 13.0000,77.6200 | 12.9500,77.5800");
        z.setActiveDrivers(45);
        z.setStatus("ACTIVE");
        z.setRestaurantEnabled(true);
        z.setDeliveryPartnerEnabled(true);
        z.setCustomerOrderingEnabled(true);
        return z;
    }

    private LocationZone createDefaultTumkurZone() {
        LocationZone z = new LocationZone();
        z.setId(TUMKUR_ZONE_ID);
        z.setZoneName(TUMKUR_ZONE_NAME);
        z.setCityName(TUMKUR_CITY);
        z.setLatitude(13.3379);
        z.setLongitude(77.1173);
        z.setRadiusKm(20.0);
        z.setPolygonCoordinates("13.3379,77.1173 | 13.3500,77.1300 | 13.3200,77.1000");
        z.setActiveDrivers(22);
        z.setStatus("ACTIVE");
        z.setRestaurantEnabled(true);
        z.setDeliveryPartnerEnabled(true);
        z.setCustomerOrderingEnabled(true);
        return z;
    }

    @Override
    @Transactional
    public ZoneSelectionResponseDto selectZone(
            UUID userCredentialId,
            String zoneId,
            String ipAddress,
            String userAgent
    ) {
        LocationZone zone = validateZone(zoneId);

        AdminUser adminUser = adminUserRepository.findByUserCredentialId(userCredentialId)
                .or(() -> adminUserRepository.findById(userCredentialId))
                .orElse(null);

        UserCredential credential = userCredentialRepository.findById(userCredentialId).orElse(null);

        String adminName = "Super Admin";
        String adminEmail = "admin@foodie.local";
        String roleName = "SUPER_ADMIN";
        UUID adminUserId = null;

        if (adminUser != null) {
            adminUserId = adminUser.getId();
            if (adminUser.getFullName() != null && !adminUser.getFullName().isBlank()) {
                adminName = adminUser.getFullName();
            }
            if (adminUser.getRole() != null) {
                roleName = adminUser.getRole().getName().name();
                if (adminUser.getRole().getName() != com.foodie.admin.entity.AdminRoleName.SUPER_ADMIN) {
                    if (adminUser.getAssignedZoneId() != null && !adminUser.getAssignedZoneId().isBlank()) {
                        String assigned = adminUser.getAssignedZoneId().trim();
                        if (!assigned.equalsIgnoreCase(zone.getId()) && !assigned.equalsIgnoreCase(zone.getZoneName())) {
                            throw new ForbiddenException("Unauthorized cross-zone selection: account is restricted to " + assigned);
                        }
                    }
                }
            }
        }

        if (credential != null && credential.getEmail() != null && !credential.getEmail().isBlank()) {
            adminEmail = credential.getEmail();
        }

        AdminZoneLoginHistory history = new AdminZoneLoginHistory(
                UUID.randomUUID(),
                adminUserId,
                adminName,
                adminEmail,
                roleName,
                zone.getId(),
                zone.getZoneName(),
                Instant.now(),
                "ACTIVE",
                ipAddress,
                userAgent
        );

        AdminZoneLoginHistory saved = loginHistoryRepository.save(history);
        log.info("Recorded Super Admin zone login: admin={} zone={} historyId={}", adminEmail, zone.getId(), saved.getId());

        if (adminUserId != null) {
            adminService.recordAudit(
                    adminUserId,
                    "ZONE_LOGIN_SELECT",
                    "LOCATION_ZONE",
                    null,
                    null,
                    Map.of(
                            "zoneId", zone.getId(),
                            "zoneName", zone.getZoneName(),
                            "historyId", saved.getId().toString()
                    )
            );
        }

        return new ZoneSelectionResponseDto(
                saved.getId(),
                zone.getId(),
                zone.getZoneName(),
                zone.getCityName(),
                adminName,
                adminEmail,
                roleName,
                saved.getLoginTime(),
                saved.getSessionStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminZoneLoginHistoryDto> getLoginHistory(String zoneId, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        String cleanZoneId = (zoneId != null && !zoneId.isBlank() && !zoneId.equalsIgnoreCase("ALL"))
                ? zoneId.trim()
                : null;
        String cleanSearch = (search != null && !search.trim().isBlank())
                ? search.trim()
                : null;

        Page<AdminZoneLoginHistory> paged = loginHistoryRepository.searchHistory(cleanZoneId, cleanSearch, pageable);
        return paged.map(h -> new AdminZoneLoginHistoryDto(
                h.getId(),
                h.getAdminUserId(),
                h.getAdminName(),
                h.getAdminEmail(),
                h.getRole(),
                h.getZoneId(),
                h.getZoneName(),
                h.getLoginTime(),
                h.getSessionStatus(),
                h.getIpAddress(),
                h.getUserAgent()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public LocationZone validateZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            throw new ResourceNotFoundException("Zone ID must not be blank.");
        }
        String cleanId = zoneId.trim();

        // 1. Look up by ID or name
        LocationZone zone = locationZoneRepository.findById(cleanId).orElse(null);
        if (zone == null) {
            zone = locationZoneRepository.findAll().stream()
                    .filter(z -> z.getId().equalsIgnoreCase(cleanId)
                            || z.getZoneName().equalsIgnoreCase(cleanId))
                    .findFirst()
                    .orElse(null);
        }

        if (zone == null) {
            // Check if matches approved zone alias or name
            if (isBangaloreZone(cleanId)) {
                zone = locationZoneRepository.findById(BANGALORE_ZONE_ID)
                        .orElseGet(this::createDefaultBangaloreZone);
            } else if (isTumkurZone(cleanId)) {
                zone = locationZoneRepository.findById(TUMKUR_ZONE_ID)
                        .orElseGet(this::createDefaultTumkurZone);
            } else {
                throw new ResourceNotFoundException("Zone not found: " + zoneId);
            }
        }

        if (!"ACTIVE".equalsIgnoreCase(zone.getStatus())) {
            throw new ForbiddenException("Zone '" + zone.getZoneName() + "' is currently INACTIVE.");
        }

        // Validate that the active zone is one of the two approved operating zones
        if (!isBangaloreZone(zone.getId()) && !isBangaloreZone(zone.getZoneName())
                && !isTumkurZone(zone.getId()) && !isTumkurZone(zone.getZoneName())) {
            throw new ForbiddenException("Invalid operating zone: '" + zone.getZoneName()
                    + "'. Only Bangalore Zone and Tumkur Zone are approved operating zones.");
        }

        return zone;
    }

    private boolean isBangaloreZone(String val) {
        if (val == null) return false;
        String v = val.trim().toLowerCase();
        return v.equals(BANGALORE_ZONE_ID.toLowerCase())
                || v.equals(BANGALORE_ZONE_NAME.toLowerCase())
                || v.equals(BANGALORE_CITY.toLowerCase())
                || v.equals("bengaluru")
                || v.equals("bengaluru zone");
    }

    private boolean isTumkurZone(String val) {
        if (val == null) return false;
        String v = val.trim().toLowerCase();
        return v.equals(TUMKUR_ZONE_ID.toLowerCase())
                || v.equals(TUMKUR_ZONE_NAME.toLowerCase())
                || v.equals(TUMKUR_CITY.toLowerCase())
                || v.equals("tumkur central")
                || v.equals("tumkur zone 1");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocationInZone(Double lat, Double lng, String city, LocationZone zone) {
        if (zone == null) {
            return true;
        }
        if (city != null && !city.isBlank()) {
            String c = city.trim().toLowerCase();
            String zName = zone.getZoneName() != null ? zone.getZoneName().toLowerCase() : "";
            String zCity = zone.getCityName() != null ? zone.getCityName().toLowerCase() : "";

            if (isBangaloreZone(zCity) || isBangaloreZone(zName) || isBangaloreZone(zone.getId())) {
                if (c.contains("bangalore") || c.contains("bengaluru")) {
                    return true;
                }
            } else if (isTumkurZone(zCity) || isTumkurZone(zName) || isTumkurZone(zone.getId())) {
                if (c.contains("tumkur") || c.contains("tumakuru")) {
                    return true;
                }
            }
        }
        if (lat != null && lng != null && zone.getLatitude() != null && zone.getLongitude() != null && zone.getRadiusKm() != null) {
            double dLat = Math.toRadians(zone.getLatitude() - lat);
            double dLng = Math.toRadians(zone.getLongitude() - lng);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(zone.getLatitude()))
                    * Math.sin(dLng / 2) * Math.sin(dLng / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = 6371 * c;
            return distance <= zone.getRadiusKm();
        }
        return false;
    }
}
