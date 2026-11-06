package com.foodie.admin.service;

import com.foodie.admin.dto.response.UserLoginDeviceResponseDto;
import java.util.List;

public interface UserLoginDeviceService {

    UserLoginDeviceResponseDto recordDeviceLogin(
            String userId,
            String userRole,
            String userAgent,
            String ipAddress,
            String location
    );

    List<UserLoginDeviceResponseDto> getDevicesForUser(String userId, boolean maskIp);

    void logoutAllDevices(String userId);

    long countActiveDevices(String userId);

    long countTotalDevices(String userId);
}

