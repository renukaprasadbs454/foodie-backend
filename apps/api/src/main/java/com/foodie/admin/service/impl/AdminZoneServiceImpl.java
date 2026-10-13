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

    @Override
    @Transactional(readOnly = true)
    public List<ActiveOperatingZoneDto> getAvailableZones(UUID userCredentialId) {
        List<LocationZone> zones = locationZoneRepository.findAll();
        return zones.stream()
                .filter(z -> "ACTIVE".equalsIgnoreCase(z.getStatus()))
                .sorted(Comparator.comparing(LocationZone::getZoneName, String.CASE_INSENSITIVE_ORDER))
                .map(z -> new ActiveOperatingZoneDto(
                        z.getId(),
                        z.getZoneName(),
                        z.getCityName(),
                        z.getLatitude(),
                        z.getLongitude(),
                        z.getRadiusKm(),
                        z.getStatus(),
                        z.isRestaurantEnabled(),
                        z.isDeliveryPartnerEnabled(),
                        z.isCustomerOrderingEnabled(),
                        z.getActiveDrivers()
                ))
                .collect(Collectors.toList());
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
        LocationZone zone = locationZoneRepository.findById(zoneId.trim()).orElse(null);
        if (zone == null) {
            // Also attempt case-insensitive match on zone_name or city_name
            zone = locationZoneRepository.findAll().stream()
                    .filter(z -> z.getId().equalsIgnoreCase(zoneId.trim())
                            || z.getZoneName().equalsIgnoreCase(zoneId.trim()))
                    .findFirst()
                    .orElse(null);
        }
        if (zone == null) {
            throw new ResourceNotFoundException("Zone not found: " + zoneId);
        }
        if (!"ACTIVE".equalsIgnoreCase(zone.getStatus())) {
            throw new ForbiddenException("Zone '" + zone.getZoneName() + "' is currently INACTIVE.");
        }
        return zone;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocationInZone(Double lat, Double lng, String city, LocationZone zone) {
        if (zone == null) {
            return true;
        }
        if (city != null && !city.isBlank() && zone.getCityName() != null) {
            if (city.trim().equalsIgnoreCase(zone.getCityName().trim())
                    || zone.getZoneName().toLowerCase().contains(city.trim().toLowerCase())
                    || city.trim().toLowerCase().contains(zone.getCityName().trim().toLowerCase())) {
                return true;
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
