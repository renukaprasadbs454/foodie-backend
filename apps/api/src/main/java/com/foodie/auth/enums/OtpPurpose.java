package com.foodie.auth.enums;

/**
 * Purpose for which an OTP is generated to prevent purpose swapping/reuse attacks.
 */
public enum OtpPurpose {
    REGISTRATION,
    LOGIN,
    PHONE_VERIFICATION,
    PASSWORD_RESET
}
