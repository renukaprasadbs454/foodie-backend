package com.foodie.infrastructure.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development SMS adapter — logs dispatch only. Swap without touching Auth module.
 * Never logs the OTP value (Phase3 §15).
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendOtp(String phoneNumber, String otp) {
        log.info("OTP SMS dispatched asynchronously for phone ending {}", mask(phoneNumber));
    }

    private static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "******" + phoneNumber.substring(phoneNumber.length() - 2);
    }
}
