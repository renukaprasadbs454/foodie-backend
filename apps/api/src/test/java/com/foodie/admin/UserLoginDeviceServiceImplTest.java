package com.foodie.admin;

import com.foodie.admin.dto.response.UserLoginDeviceResponseDto;
import com.foodie.admin.entity.UserLoginDevice;
import com.foodie.admin.repository.AdminUserRepository;
import com.foodie.admin.repository.UserLoginDeviceRepository;
import com.foodie.admin.service.impl.UserLoginDeviceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLoginDeviceServiceImplTest {

    @Mock
    private UserLoginDeviceRepository deviceRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    private UserLoginDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserLoginDeviceServiceImpl(deviceRepository, adminUserRepository);
    }

    @Test
    void recordDeviceLogin_newDevice_createsAndReturnsDto() {
        String userId = "user-123";
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
        String ip = "192.168.1.100";
        String location = "Bengaluru, IN";

        when(deviceRepository.findFirstByUserIdAndDeviceNameAndBrowserOrderByLastActiveAtDesc(eq(userId), anyString(), eq("Chrome")))
                .thenReturn(Optional.empty());

        when(deviceRepository.save(any(UserLoginDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLoginDeviceResponseDto result = service.recordDeviceLogin(userId, "FINANCE_ADMIN", ua, ip, location);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.userRole()).isEqualTo("FINANCE_ADMIN");
        assertThat(result.browser()).isEqualTo("Chrome");
        assertThat(result.os()).isEqualTo("Windows 11");
        assertThat(result.deviceName()).isEqualTo("Windows PC");
        assertThat(result.ipAddress()).isEqualTo(ip);
        assertThat(result.location()).isEqualTo(location);
        assertThat(result.isCurrentActive()).isTrue();

        verify(deviceRepository).save(any(UserLoginDevice.class));
    }

    @Test
    void recordDeviceLogin_existingDevice_updatesLastActive() {
        String userId = "user-123";
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        UserLoginDevice existing = new UserLoginDevice(
                UUID.randomUUID(),
                userId,
                "OPS",
                "iPhone",
                "Safari",
                "iOS",
                "10.0.0.1",
                "Old Location",
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(3600),
                false,
                Instant.now().minusSeconds(1800)
        );

        when(deviceRepository.findFirstByUserIdAndDeviceNameAndBrowserOrderByLastActiveAtDesc(eq(userId), eq("iPhone"), eq("Safari")))
                .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(UserLoginDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserLoginDeviceResponseDto result = service.recordDeviceLogin(userId, "OPS", ua, "192.168.1.200", "Tumkur, IN");

        assertThat(result).isNotNull();
        assertThat(result.isCurrentActive()).isTrue();
        assertThat(result.logoutAt()).isNull();
        assertThat(result.ipAddress()).isEqualTo("192.168.1.200");
        assertThat(result.location()).isEqualTo("Tumkur, IN");
        assertThat(result.browser()).isEqualTo("Safari");
        assertThat(result.os()).isEqualTo("iOS");
        assertThat(result.deviceName()).isEqualTo("iPhone");
    }

    @Test
    void recordDeviceLogin_prunesDevicesBeyond10() {
        String userId = "user-prune";
        List<UserLoginDevice> devices = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            devices.add(new UserLoginDevice(
                    UUID.randomUUID(),
                    userId,
                    "ADMIN",
                    "Device " + i,
                    "Chrome",
                    "Windows",
                    "127.0.0.1",
                    "Bengaluru",
                    Instant.now().minusSeconds(i * 100),
                    Instant.now().minusSeconds(i * 100),
                    true,
                    null
            ));
        }

        when(deviceRepository.findFirstByUserIdAndDeviceNameAndBrowserOrderByLastActiveAtDesc(eq(userId), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(UserLoginDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceRepository.findByUserIdOrderByLastActiveAtDesc(userId)).thenReturn(devices);

        service.recordDeviceLogin(userId, "ADMIN", "Chrome on Windows", "127.0.0.1", "Bengaluru");

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(deviceRepository).deleteByIdIn(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void getDevicesForUser_masksIpWhenRequested() {
        String userId = "user-ip-mask";
        UserLoginDevice dev = new UserLoginDevice(
                UUID.randomUUID(),
                userId,
                "SUPER_ADMIN",
                "MacBook Pro",
                "Chrome",
                "macOS",
                "49.207.215.89",
                "Bengaluru",
                Instant.now(),
                Instant.now(),
                true,
                null
        );

        when(deviceRepository.findByUserIdOrderByLastActiveAtDesc(userId)).thenReturn(List.of(dev));

        List<UserLoginDeviceResponseDto> unmasked = service.getDevicesForUser(userId, false);
        assertThat(unmasked.get(0).ipAddress()).isEqualTo("49.207.215.89");

        List<UserLoginDeviceResponseDto> masked = service.getDevicesForUser(userId, true);
        assertThat(masked.get(0).ipAddress()).isEqualTo("49.207.xxx.xxx");
    }

    @Test
    void logoutAllDevices_callsRepositoryLogout() {
        String userId = "user-logout";
        service.logoutAllDevices(userId);
        verify(deviceRepository).logoutAllByUserId(eq(userId), any(Instant.class));
    }
}

