package com.foodie.auth.service;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;

public interface OtpService {

    /**
     * Generates a cryptographically secure 6-digit OTP, stores its hash in Redis,
     * enforces resend cooldowns, and dispatches the OTP via WhatsApp Cloud API.
     */
    void generateAndSendOtp(String phoneNumber, OtpUserType userType, OtpPurpose purpose);

    /**
     * Verifies an OTP code against stored hash in Redis.
     * Enforces maximum attempt limits and invalidates the OTP upon successful verification.
     */
    void verifyOtp(String phoneNumber, String otp, OtpUserType userType, OtpPurpose purpose);

    /**
     * Manually invalidates an OTP for a given phone, user type, and purpose.
     */
    void invalidateOtp(String phoneNumber, OtpUserType userType, OtpPurpose purpose);
}
