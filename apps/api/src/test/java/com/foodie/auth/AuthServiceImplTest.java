package com.foodie.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodie.auth.dto.request.GoogleAuthRequestDto;
import com.foodie.auth.dto.request.VerifyOtpRequestDto;
import com.foodie.auth.dto.response.TokenPairResponseDto;
import com.foodie.auth.entity.UserCredential;
import com.foodie.auth.exception.InvalidOtpException;
import com.foodie.auth.exception.OtpExpiredException;
import com.foodie.auth.repository.RefreshTokenRepository;
import com.foodie.auth.repository.UserCredentialRepository;
import com.foodie.auth.service.impl.AuthServiceImpl;
import com.foodie.common.enums.UserType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.infrastructure.google.GoogleTokenVerifier;
import com.foodie.infrastructure.sms.SmsSender;
import com.foodie.security.jwt.JwtTokenProvider;
import com.foodie.security.ratelimit.RedisRateLimiter;
import com.foodie.shared.event.UserCredentialCreatedEvent;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RedisRateLimiter rateLimiter;
    @Mock private SmsSender smsSender;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        authService = new AuthServiceImpl(
                userCredentialRepository,
                refreshTokenRepository,
                redisTemplate,
                rateLimiter,
                passwordEncoder,
                smsSender,
                googleTokenVerifier,
                jwtTokenProvider,
                eventPublisher
        );
    }

    @Test
    void requestOtp_storesHashedOtpAndDispatchesSms() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));

        authService.requestOtp("+919876543210");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("otp:+919876543210"), hashCaptor.capture(), eq(Duration.ofMinutes(5)));
        assertThat(hashCaptor.getValue()).startsWith("$2");
        verify(smsSender, timeout(2000)).sendOtp(eq("+919876543210"), anyString());
    }

    @Test
    void verifyOtp_expiredOtp_throwsOtpExpiredException() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));
        when(valueOperations.get("otp:+919876543210")).thenReturn(null);

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequestDto("+919876543210", "123456", UserType.CUSTOMER, null)
        )).isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void verifyOtp_invalidOtp_throwsInvalidOtpException() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));
        when(valueOperations.get("otp:+919876543210")).thenReturn(passwordEncoder.encode("111111"));

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequestDto("+919876543210", "999999", UserType.CUSTOMER, null)
        )).isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyOtp_newUserWithoutUserType_throwsUserTypeRequired() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));
        when(valueOperations.get("otp:+919876543210")).thenReturn(passwordEncoder.encode("123456"));

        assertThatThrownBy(() -> authService.verifyOtp(
                new VerifyOtpRequestDto("+919876543210", "123456", null, null)
        )).isInstanceOf(BadRequestException.class)
                .extracting(ex -> ((BadRequestException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_TYPE_REQUIRED);
    }

    @Test
    void verifyOtp_existingUserSameType_issuesTokenPair() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));
        when(valueOperations.get("otp:+919876543210")).thenReturn(passwordEncoder.encode("123456"));

        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.phoneSignup("+919876543210", UserType.CUSTOMER);
        ReflectionTestUtils.setField(credential, "id", userId);

        when(userCredentialRepository.findAllByPhoneNumber("+919876543210"))
                .thenReturn(java.util.List.of(credential));
        when(userCredentialRepository.findByPhoneNumberAndUserType("+919876543210", UserType.CUSTOMER))
                .thenReturn(Optional.of(credential));
        when(jwtTokenProvider.createAccessToken(userId, UserType.CUSTOMER)).thenReturn("access");
        when(jwtTokenProvider.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenProvider.refreshTtlSeconds(UserType.CUSTOMER)).thenReturn(2592000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPairResponseDto response = authService.verifyOtp(
                new VerifyOtpRequestDto("+919876543210", "123456", UserType.CUSTOMER, "device")
        );

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.userType()).isEqualTo(UserType.CUSTOMER);
        assertThat(response.isNewUser()).isFalse();
        assertThat(response.refreshToken()).isNotBlank();
        verify(redisTemplate).delete("otp:+919876543210");
    }

    @Test
    void verifyOtp_samePhoneDifferentType_createsSecondCredential() {
        doNothing().when(rateLimiter).check(anyString(), anyInt(), any(Duration.class));
        when(valueOperations.get("otp:+919876543210")).thenReturn(passwordEncoder.encode("123456"));

        UserCredential customer = UserCredential.phoneSignup("+919876543210", UserType.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());

        when(userCredentialRepository.findAllByPhoneNumber("+919876543210"))
                .thenReturn(java.util.List.of(customer));
        when(userCredentialRepository.findByPhoneNumberAndUserType("+919876543210", UserType.RESTAURANT))
                .thenReturn(Optional.empty());
        when(userCredentialRepository.save(any())).thenAnswer(inv -> {
            UserCredential saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(jwtTokenProvider.createAccessToken(any(), eq(UserType.RESTAURANT))).thenReturn("access-rest");
        when(jwtTokenProvider.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenProvider.refreshTtlSeconds(UserType.RESTAURANT)).thenReturn(2592000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPairResponseDto response = authService.verifyOtp(
                new VerifyOtpRequestDto("+919876543210", "123456", UserType.RESTAURANT, "device")
        );

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.userType()).isEqualTo(UserType.RESTAURANT);
        assertThat(response.accessToken()).isEqualTo("access-rest");
        verify(eventPublisher).publishEvent(any(UserCredentialCreatedEvent.class));
    }

    @Test
    void authenticateWithGoogle_newCustomer_issuesTokenPair() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(
                new GoogleTokenVerifier.GoogleIdentity("google-sub-1", "user@example.com", true)
        );
        when(userCredentialRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userCredentialRepository.save(any())).thenAnswer(inv -> {
            UserCredential saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(jwtTokenProvider.createAccessToken(any(), eq(UserType.CUSTOMER))).thenReturn("access");
        when(jwtTokenProvider.accessTokenTtlSeconds()).thenReturn(900L);
        when(jwtTokenProvider.refreshTtlSeconds(UserType.CUSTOMER)).thenReturn(2592000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenPairResponseDto response = authService.authenticateWithGoogle(
                new GoogleAuthRequestDto("id-token", "Pixel")
        );

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.userType()).isEqualTo(UserType.CUSTOMER);
        verify(eventPublisher).publishEvent(any(UserCredentialCreatedEvent.class));
    }
}
