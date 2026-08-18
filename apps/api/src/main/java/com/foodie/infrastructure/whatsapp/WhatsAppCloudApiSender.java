package com.foodie.infrastructure.whatsapp;

import com.foodie.auth.enums.OtpPurpose;
import com.foodie.auth.enums.OtpUserType;
import com.foodie.common.exception.BadRequestException;
import com.foodie.common.exception.ErrorCode;
import com.foodie.common.util.PhoneUtils;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WhatsAppCloudApiSender implements WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiSender.class);

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    @Autowired
    public WhatsAppCloudApiSender(WhatsAppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    // Package-private constructor for testing with custom RestClient
    WhatsAppCloudApiSender(WhatsAppProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public void sendOtp(String phoneNumber, String otp, OtpUserType userType, OtpPurpose purpose) {
        String maskedPhone = PhoneUtils.mask(phoneNumber);

        if (!properties.isEnabled()) {
            log.info("WhatsApp Cloud API disabled. Suppressed OTP send for userType={} purpose={} phone={}",
                    userType, purpose, maskedPhone);
            return;
        }

        String recipientPhoneDigits = PhoneUtils.formatForWhatsApp(phoneNumber);

        if (properties.getPhoneNumberId() == null || properties.getPhoneNumberId().isBlank()) {
            log.error("WHATSAPP_PHONE_NUMBER_ID is missing or blank.");
            throw new BadRequestException(ErrorCode.EXTERNAL_SERVICE_ERROR, "WhatsApp service configuration error.");
        }

        String url = String.format("https://graph.facebook.com/%s/%s/messages",
                properties.getApiVersion(), properties.getPhoneNumberId());

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", recipientPhoneDigits,
                "type", "template",
                "template", Map.of(
                        "name", properties.getOtpTemplateName(),
                        "language", Map.of("code", properties.getOtpTemplateLanguage()),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", List.of(
                                                Map.of("type", "text", "text", otp)
                                        )
                                ),
                                Map.of(
                                        "type", "button",
                                        "sub_type", "url",
                                        "index", "0",
                                        "parameters", List.of(
                                                Map.of("type", "text", "text", otp)
                                        )
                                )
                        )
                )
        );

        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("WhatsApp OTP dispatched successfully for userType={} purpose={} phone={}",
                    userType, purpose, maskedPhone);
        } catch (Exception ex) {
            log.error("Failed to send WhatsApp OTP to phone={}: {}", maskedPhone, ex.getMessage());
            throw new BadRequestException(ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Failed to deliver WhatsApp OTP. Please try again later.");
        }
    }
}
