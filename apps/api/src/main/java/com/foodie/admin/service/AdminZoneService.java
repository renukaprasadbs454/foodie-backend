package com.foodie.admin.service;

import com.foodie.admin.dto.ActiveOperatingZoneDto;
import com.foodie.admin.dto.AdminZoneLoginHistoryDto;
import com.foodie.admin.dto.ZoneSelectionResponseDto;
import com.foodie.admin.entity.LocationZone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface AdminZoneService {

    List<ActiveOperatingZoneDto> getAvailableZones(UUID userCredentialId);

    ZoneSelectionResponseDto selectZone(UUID userCredentialId, String zoneId, String ipAddress, String userAgent);

    Page<AdminZoneLoginHistoryDto> getLoginHistory(String zoneId, String search, int page, int size);

    LocationZone validateZone(String zoneId);

    boolean isLocationInZone(Double lat, Double lng, String city, LocationZone zone);
}
