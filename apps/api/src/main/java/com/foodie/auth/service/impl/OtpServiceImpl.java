package com.foodie.auth.service.impl;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.auth.exception.InvalidOtpException;
import com.foodie.auth.exception.OtpExpiredException;
import com.foodie.auth.service.OtpService;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.exception.RateLimitedException;
import com.foodie.common.util.PhoneUtils;
import com.foodie.infrastructure.whatsapp.WhatsAppService;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN_TTL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final WhatsAppService whatsAppService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpServiceImpl(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            WhatsAppService whatsAppService
    ) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.whatsAppService = whatsAppService;
    }

    @Override
    public void generateAndSendOtp(String phoneNumber, OtpUserType userType, OtpPurpose purpose) {
        if (userType == null) {
            throw new BadRequestException(ErrorCode.USER_TYPE_REQUIRED, "userType is required for OTP request.");
        }
        if (purpose == null) {
            purpose = OtpPurpose.REGISTRATION;
        }

        String normalized = PhoneUtils.normalize(phoneNumber);
        String cKey = cooldownKey(userType, purpose, normalized);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cKey))) {
            throw new RateLimitedException(60L);
        }

        // Generate cryptographically secure 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        String otpHash = passwordEncoder.encode(otp);

        String key = otpKey(userType, purpose, normalized);
        redisTemplate.opsForValue().set(key, otpHash, OTP_TTL);
        redisTemplate.opsForValue().set(cKey, "1", RESEND_COOLDOWN_TTL);
        redisTemplate.delete(attemptsKey(userType, purpose, normalized));

        log.info("Generated OTP for userType={} purpose={} phone={}", userType, purpose, PhoneUtils.mask(normalized));

        whatsAppService.sendOtp(normalized, otp, userType, purpose);
    }

    @Override
    public void verifyOtp(String phoneNumber, String otp, OtpUserType userType, OtpPurpose purpose) {
        if (userType == null) {
            throw new BadRequestException(ErrorCode.USER_TYPE_REQUIRED, "userType is required for OTP verify.");
        }
        if (purpose == null) {
            purpose = OtpPurpose.REGISTRATION;
        }

        String normalized = PhoneUtils.normalize(phoneNumber);
        String attKey = attemptsKey(userType, purpose, normalized);
        String key = otpKey(userType, purpose, normalized);

        Long attempts = redisTemplate.opsForValue().increment(attKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attKey, OTP_TTL);
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            throw new RateLimitedException(300L);
        }

        String storedHash = redisTemplate.opsForValue().get(key);
        if (storedHash == null) {
            throw new OtpExpiredException();
        }

        if (!passwordEncoder.matches(otp, storedHash)) {
            throw new InvalidOtpException();
        }

        // On successful verification: delete OTP state
        redisTemplate.delete(key);
        redisTemplate.delete(attKey);
        redisTemplate.delete(cooldownKey(userType, purpose, normalized));

        log.info("OTP verified successfully for userType={} purpose={} phone={}",
                userType, purpose, PhoneUtils.mask(normalized));
    }

    @Override
    public void invalidateOtp(String phoneNumber, OtpUserType userType, OtpPurpose purpose) {
        if (phoneNumber == null || userType == null || purpose == null) {
            return;
        }
        String normalized = PhoneUtils.normalize(phoneNumber);
        redisTemplate.delete(otpKey(userType, purpose, normalized));
        redisTemplate.delete(attemptsKey(userType, purpose, normalized));
        redisTemplate.delete(cooldownKey(userType, purpose, normalized));
    }

    private static String otpKey(OtpUserType userType, OtpPurpose purpose, String normalizedPhone) {
        return String.format("otp:%s:%s:%s", userType.name(), purpose.name(), normalizedPhone);
    }

    private static String cooldownKey(OtpUserType userType, OtpPurpose purpose, String normalizedPhone) {
        return String.format("ratelimit:otp-cooldown:%s:%s:%s", userType.name(), purpose.name(), normalizedPhone);
    }

    private static String attemptsKey(OtpUserType userType, OtpPurpose purpose, String normalizedPhone) {
        return String.format("ratelimit:otp-attempts:%s:%s:%s", userType.name(), purpose.name(), normalizedPhone);
    }
}
