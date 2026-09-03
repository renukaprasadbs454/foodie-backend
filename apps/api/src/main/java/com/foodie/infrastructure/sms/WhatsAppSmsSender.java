package com.foodie.infrastructure.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Primary
@Component
public class WhatsAppSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSmsSender.class);

    private final RestTemplate restTemplate;

    @Value("${whatsapp.api-url:https://graph.facebook.com/v20.0}")
    private String apiUrl;

    @Value("${whatsapp.phone-number-id:123456789012345}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token:placeholder_access_token}")
    private String accessToken;

    public WhatsAppSmsSender() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void sendOtp(String phoneNumber, String otp) {
        log.info("Sending real WhatsApp OTP to phone: {}", phoneNumber);

        try {
            // Remove + from phone if present, WhatsApp API expects clean country code
            String formattedPhone = phoneNumber.replaceAll("[^0-9]", "");

            String url = String.format("%s/%s/messages", apiUrl, phoneNumberId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            // Constructing WhatsApp JSON Payload for a standard OTP template
            // Free-form text is rejected by Meta outside the 24-hour service window.
            String payload = String.format(
                    """
                            {
                              "messaging_product": "whatsapp",
                              "to": "%s",
                              "type": "template",
                              "template": {
                                "name": "login_otp",
                                "language": {
                                  "code": "en_US"
                                },
                                "components": [
                                  {
                                    "type": "body",
                                    "parameters": [
                                      {
                                        "type": "text",
                                        "text": "%s"
                                      }
                                    ]
                                  },
                                  {
                                    "type": "button",
                                    "sub_type": "url",
                                    "index": "0",
                                    "parameters": [
                                      {
                                        "type": "text",
                                        "text": "%s"
                                      }
                                    ]
                                  }
                                ]
                              }
                            }
                            """,
                    formattedPhone, otp, otp);

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            log.info("Successfully dispatched WhatsApp OTP to {}", mask(formattedPhone));

        } catch (Exception ex) {
            log.error("Failed to send WhatsApp OTP to {}: {}", mask(phoneNumber), ex.getMessage());
            // Fallback for development if misconfigured
            log.error("OTP would have been: {}", otp);
        }
    }

    private static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "******" + phoneNumber.substring(phoneNumber.length() - 2);
    }
}
