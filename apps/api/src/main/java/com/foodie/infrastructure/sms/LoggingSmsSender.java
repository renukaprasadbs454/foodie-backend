package com.foodie.infrastructure.sms;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.infrastructure.whatsapp.WhatsAppService;
import org.springframework.stereotype.Component;

/**
 * Adapter delegating legacy SmsSender calls to the centralized WhatsAppService.
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private final WhatsAppService whatsAppService;

    public LoggingSmsSender(WhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    @Override
    public void sendOtp(String phoneNumber, String otp) {
        whatsAppService.sendOtp(phoneNumber, otp, OtpUserType.CUSTOMER, OtpPurpose.REGISTRATION);
    }
}
