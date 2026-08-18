package com.foodie.infrastructure.whatsapp;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;

public interface WhatsAppService {
    
    /**
     * Dispatches an OTP code via WhatsApp Cloud API to the given phone number.
     *
     * @param phoneNumber The recipient's phone number.
     * @param otp The 6-digit OTP code.
     * @param userType The target user/account type.
     * @param purpose The purpose of OTP generation.
     */
    void sendOtp(String phoneNumber, String otp, OtpUserType userType, OtpPurpose purpose);
}
