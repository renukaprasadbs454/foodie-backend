package com.foodie.admin.service.impl;

import com.foodie.admin.dto.response.UserLoginDeviceResponseDto;
import com.foodie.admin.entity.AdminUser;
import com.foodie.admin.entity.UserLoginDevice;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.UserLoginDeviceRepository;
import com.foodie.admin.service.UserLoginDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserLoginDeviceServiceImpl implements UserLoginDeviceService {

    private static final Logger log = LoggerFactory.getLogger(UserLoginDeviceServiceImpl.class);
    private static final int MAX_DEVICES_PER_USER = 10;

    private final UserLoginDeviceRepository deviceRepository;
    private final AdminUserRepository adminUserRepository;

    @Autowired
    public UserLoginDeviceServiceImpl(
            UserLoginDeviceRepository deviceRepository,
            AdminUserRepository adminUserRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.adminUserRepository = adminUserRepository;
    }

    public UserLoginDeviceServiceImpl(UserLoginDeviceRepository deviceRepository) {
        this(deviceRepository, null);
    }

    @Override
    @Transactional
    public UserLoginDeviceResponseDto recordDeviceLogin(
            String userId,
            String userRole,
            String userAgent,
            String ipAddress,
            String location
    ) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        String cleanUserId = userId.trim();
        String safeIp = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress.trim() : "127.0.0.1";
        String safeLocation = (location != null && !location.isBlank()) ? location.trim() : "Bengaluru, IN";

        String os = parseOperatingSystem(userAgent);
        String browser = parseBrowser(userAgent);
        String deviceName = parseDeviceName(userAgent, os);

        Optional<UserLoginDevice> existing = deviceRepository
                .findFirstByUserIdAndDeviceNameAndBrowserOrderByLastActiveAtDesc(cleanUserId, deviceName, browser);

        UserLoginDevice savedDevice;
        if (existing.isPresent()) {
            UserLoginDevice device = existing.get();
            device.refreshActive(safeIp, safeLocation);
            if (userRole != null && !userRole.isBlank()) {
                device.setUserRole(userRole.trim());
            }
            savedDevice = deviceRepository.save(device);
            log.info("Updated active login device for userId={}, device={}, browser={}", cleanUserId, deviceName, browser);
        } else {
            UserLoginDevice newDevice = new UserLoginDevice(
                    UUID.randomUUID(),
                    cleanUserId,
                    userRole != null ? userRole.trim() : "ADMIN",
                    deviceName,
                    browser,
                    os,
                    safeIp,
                    safeLocation,
                    Instant.now(),
                    Instant.now(),
                    true,
                    null
            );
            savedDevice = deviceRepository.save(newDevice);
            log.info("Recorded new login device for userId={}, device={}, browser={}", cleanUserId, deviceName, browser);
        }

        pruneOldDevices(cleanUserId);
        return toDto(savedDevice, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserLoginDeviceResponseDto> getDevicesForUser(String userId, boolean maskIp) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String cleanId = userId.trim();
        Set<String> targetIds = resolveAllUserIds(cleanId);

        List<UserLoginDevice> combined = new ArrayList<>();
        Set<UUID> seenIds = new HashSet<>();

        for (String id : targetIds) {
            List<UserLoginDevice> found = deviceRepository.findByUserIdOrderByLastActiveAtDesc(id);
            for (UserLoginDevice d : found) {
                if (seenIds.add(d.getId())) {
                    combined.add(d);
                }
            }
        }

        combined.sort((a, b) -> b.getLastActiveAt().compareTo(a.getLastActiveAt()));

        return combined.stream()
                .map(device -> toDto(device, maskIp))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void logoutAllDevices(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String cleanId = userId.trim();
        Set<String> targetIds = resolveAllUserIds(cleanId);
        int total = 0;
        for (String id : targetIds) {
            total += deviceRepository.logoutAllByUserId(id, Instant.now());
        }
        log.info("Logged out all active devices for targetIds={}, total={}", targetIds, total);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveDevices(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        Set<String> targetIds = resolveAllUserIds(userId.trim());
        long count = 0;
        for (String id : targetIds) {
            count += deviceRepository.countByUserIdAndIsCurrentActiveTrue(id);
        }
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public long countTotalDevices(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        Set<String> targetIds = resolveAllUserIds(userId.trim());
        long count = 0;
        for (String id : targetIds) {
            count += deviceRepository.countByUserId(id);
        }
        return count;
    }

    private Set<String> resolveAllUserIds(String id) {
        Set<String> ids = new HashSet<>();
        ids.add(id);
        if (adminUserRepository != null) {
            try {
                UUID uuid = UUID.fromString(id);
                adminUserRepository.findById(uuid).ifPresent(admin -> {
                    if (admin.getUserCredentialId() != null) {
                        ids.add(admin.getUserCredentialId().toString());
                    }
                });
                adminUserRepository.findByUserCredentialId(uuid).ifPresent(admin -> {
                    if (admin.getId() != null) {
                        ids.add(admin.getId().toString());
                    }
                });
            } catch (IllegalArgumentException ignored) {
                // Not a UUID, skip UUID resolution
            }
        }
        return ids;
    }

    private void pruneOldDevices(String userId) {
        try {
            List<UserLoginDevice> allDevices = deviceRepository.findByUserIdOrderByLastActiveAtDesc(userId);
            if (allDevices.size() > MAX_DEVICES_PER_USER) {
                List<UUID> idsToDelete = new ArrayList<>();
                for (int i = MAX_DEVICES_PER_USER; i < allDevices.size(); i++) {
                    idsToDelete.add(allDevices.get(i).getId());
                }
                if (!idsToDelete.isEmpty()) {
                    deviceRepository.deleteByIdIn(idsToDelete);
                    log.info("Pruned {} old login devices for userId={}", idsToDelete.size(), userId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to prune old login devices for user {}: {}", userId, e.getMessage());
        }
    }

    private UserLoginDeviceResponseDto toDto(UserLoginDevice entity, boolean maskIp) {
        String ip = entity.getIpAddress();
        if (maskIp && ip != null) {
            ip = maskIpAddress(ip);
        }
        return new UserLoginDeviceResponseDto(
                entity.getId(),
                entity.getUserId(),
                entity.getUserRole(),
                entity.getDeviceName(),
                entity.getBrowser(),
                entity.getOs(),
                ip,
                entity.getLocation(),
                entity.getLoginAt(),
                entity.getLastActiveAt(),
                entity.isCurrentActive(),
                entity.getLogoutAt()
        );
    }

    public static String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return "192.168.xxx.xxx";
        }
        String clean = ip.trim();
        String[] parts = clean.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".xxx.xxx";
        }
        if (clean.contains(":")) {
            String[] v6Parts = clean.split(":");
            if (v6Parts.length >= 2) {
                return v6Parts[0] + ":" + v6Parts[1] + ":xxxx:xxxx";
            }
        }
        return "192.168.xxx.xxx";
    }

    public static String parseOperatingSystem(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Windows";
        }
        String u = ua.toLowerCase();
        if (u.contains("windows nt 10.0") || u.contains("windows nt 11.0") || u.contains("windows 11") || u.contains("windows 10")) {
            return "Windows 11";
        }
        if (u.contains("windows")) {
            return "Windows";
        }
        if (u.contains("iphone")) {
            return "iOS";
        }
        if (u.contains("ipad")) {
            return "iPadOS";
        }
        if (u.contains("android")) {
            return "Android";
        }
        if (u.contains("mac os") || u.contains("macintosh")) {
            return "macOS";
        }
        if (u.contains("linux")) {
            return "Linux";
        }
        return "Windows";
    }

    public static String parseBrowser(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Chrome";
        }
        String u = ua.toLowerCase();
        if (u.contains("edg/") || u.contains("edge/")) {
            return "Edge";
        }
        if (u.contains("opr/") || u.contains("opera")) {
            return "Opera";
        }
        if (u.contains("chrome/") && !u.contains("chromium")) {
            return "Chrome";
        }
        if (u.contains("firefox/")) {
            return "Firefox";
        }
        if (u.contains("safari/") && !u.contains("chrome")) {
            return "Safari";
        }
        return "Chrome";
    }

    public static String parseDeviceName(String ua, String os) {
        if (ua == null || ua.isBlank()) {
            return "Windows PC";
        }
        String u = ua.toLowerCase();
        if (u.contains("iphone")) {
            return "iPhone";
        }
        if (u.contains("ipad")) {
            return "iPad";
        }
        if (u.contains("android")) {
            return u.contains("mobile") ? "Android Phone" : "Android Tablet";
        }
        if (u.contains("macintosh") || u.contains("mac os")) {
            return "MacBook Pro";
        }
        if (u.contains("windows")) {
            return "Windows PC";
        }
        if (u.contains("linux")) {
            return "Linux Workstation";
        }
        return os != null ? os + " Device" : "Desktop PC";
    }
}
