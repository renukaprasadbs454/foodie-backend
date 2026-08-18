package com.foodie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.auth.exception.InvalidOtpException;
import com.foodie.auth.exception.OtpExpiredException;
import com.foodie.auth.service.impl.OtpServiceImpl;
import com.foodie.common.exception.RateLimitedException;
import com.foodie.infrastructure.whatsapp.WhatsAppService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WhatsAppService whatsAppService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        otpService = new OtpServiceImpl(redisTemplate, passwordEncoder, whatsAppService);
    }

    @Test
    void generateAndSendOtp_storesHashedOtpAndCallsWhatsAppService() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        otpService.generateAndSendOtp("+919876543210", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("otp:CUSTOMER:REGISTRATION:+919876543210"), hashCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(hashCaptor.getValue()).startsWith("$2");
        verify(whatsAppService).sendOtp(eq("+919876543210"), anyString(), eq(OtpUserType.CUSTOMER), eq(OtpPurpose.REGISTRATION));
    }

    @Test
    void generateAndSendOtp_cooldownActive_throwsRateLimitedException() {
        when(redisTemplate.hasKey("ratelimit:otp-cooldown:RESTAURANT:REGISTRATION:+919876543210")).thenReturn(true);

        assertThatThrownBy(() ->
                otpService.generateAndSendOtp("+919876543210", OtpUserType.RESTAURANT, OtpPurpose.REGISTRATION)
        ).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void verifyOtp_validOtp_deletesKeysAndSucceeds() {
        String hashedOtp = passwordEncoder.encode("123456");
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("otp:DELIVERY_PARTNER:REGISTRATION:+919876543210")).thenReturn(hashedOtp);

        otpService.verifyOtp("+919876543210", "123456", OtpUserType.DELIVERY_PARTNER, OtpPurpose.REGISTRATION);

        verify(redisTemplate).delete("otp:DELIVERY_PARTNER:REGISTRATION:+919876543210");
    }

    @Test
    void verifyOtp_expiredOtp_throwsOtpExpiredException() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("otp:CUSTOMER:REGISTRATION:+919876543210")).thenReturn(null);

        assertThatThrownBy(() ->
                otpService.verifyOtp("+919876543210", "123456", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION)
        ).isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void verifyOtp_invalidCode_throwsInvalidOtpException() {
        String hashedOtp = passwordEncoder.encode("123456");
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get("otp:CUSTOMER:REGISTRATION:+919876543210")).thenReturn(hashedOtp);

        assertThatThrownBy(() ->
                otpService.verifyOtp("+919876543210", "999999", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION)
        ).isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyOtp_maxAttemptsExceeded_locksOutAndDeletesOtp() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        assertThatThrownBy(() ->
                otpService.verifyOtp("+919876543210", "123456", OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION)
        ).isInstanceOf(RateLimitedException.class);

        verify(redisTemplate).delete("otp:CUSTOMER:REGISTRATION:+919876543210");
    }
}
