package com.foodie.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_login_devices")
public class UserLoginDevice {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "location", length = 150)
    private String location;

    @Column(name = "login_at", nullable = false)
    private Instant loginAt = Instant.now();

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt = Instant.now();

    @Column(name = "is_current_active", nullable = false)
    private boolean isCurrentActive = true;

    @Column(name = "logout_at")
    private Instant logoutAt;

    public UserLoginDevice() {
    }

    public UserLoginDevice(
            UUID id,
            String userId,
            String userRole,
            String deviceName,
            String browser,
            String os,
            String ipAddress,
            String location,
            Instant loginAt,
            Instant lastActiveAt,
            boolean isCurrentActive,
            Instant logoutAt
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.userId = userId;
        this.userRole = userRole;
        this.deviceName = deviceName;
        this.browser = browser;
        this.os = os;
        this.ipAddress = ipAddress;
        this.location = location;
        this.loginAt = loginAt != null ? loginAt : Instant.now();
        this.lastActiveAt = lastActiveAt != null ? lastActiveAt : Instant.now();
        this.isCurrentActive = isCurrentActive;
        this.logoutAt = logoutAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Instant getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(Instant loginAt) {
        this.loginAt = loginAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public boolean isCurrentActive() {
        return isCurrentActive;
    }

    public void setCurrentActive(boolean currentActive) {
        isCurrentActive = currentActive;
    }

    public Instant getLogoutAt() {
        return logoutAt;
    }

    public void setLogoutAt(Instant logoutAt) {
        this.logoutAt = logoutAt;
    }

    public void refreshActive(String ipAddress, String location) {
        this.lastActiveAt = Instant.now();
        this.isCurrentActive = true;
        this.logoutAt = null;
        if (ipAddress != null && !ipAddress.isBlank()) {
            this.ipAddress = ipAddress;
        }
        if (location != null && !location.isBlank()) {
            this.location = location;
        }
    }

    public void logout() {
        this.isCurrentActive = false;
        this.logoutAt = Instant.now();
    }
}

