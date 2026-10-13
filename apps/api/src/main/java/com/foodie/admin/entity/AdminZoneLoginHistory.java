package com.foodie.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_zone_login_history")
public class AdminZoneLoginHistory {

    @Id
    private UUID id;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Column(name = "admin_name", nullable = false)
    private String adminName;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "zone_id", nullable = false, length = 100)
    private String zoneId;

    @Column(name = "zone_name", nullable = false, length = 200)
    private String zoneName;

    @Column(name = "login_time", nullable = false)
    private Instant loginTime = Instant.now();

    @Column(name = "session_status", nullable = false, length = 50)
    private String sessionStatus = "ACTIVE";

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public AdminZoneLoginHistory() {
    }

    public AdminZoneLoginHistory(
            UUID id,
            UUID adminUserId,
            String adminName,
            String adminEmail,
            String role,
            String zoneId,
            String zoneName,
            Instant loginTime,
            String sessionStatus,
            String ipAddress,
            String userAgent
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.adminUserId = adminUserId;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.role = role;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.loginTime = loginTime != null ? loginTime : Instant.now();
        this.sessionStatus = sessionStatus != null ? sessionStatus : "ACTIVE";
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Instant getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Instant loginTime) {
        this.loginTime = loginTime;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
