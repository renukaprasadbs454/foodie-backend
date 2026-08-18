package com.foodie.infrastructure.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {

    private boolean enabled = true;
    private String accessToken = "";
    private String phoneNumberId = "";
    private String businessAccountId = "";
    private String apiVersion = "v18.0";
    private String otpTemplateName = "foodie_otp_verify";
    private String otpTemplateLanguage = "en";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getBusinessAccountId() {
        return businessAccountId;
    }

    public void setBusinessAccountId(String businessAccountId) {
        this.businessAccountId = businessAccountId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getOtpTemplateName() {
        return otpTemplateName;
    }

    public void setOtpTemplateName(String otpTemplateName) {
        this.otpTemplateName = otpTemplateName;
    }

    public String getOtpTemplateLanguage() {
        return otpTemplateLanguage;
    }

    public void setOtpTemplateLanguage(String otpTemplateLanguage) {
        this.otpTemplateLanguage = otpTemplateLanguage;
    }
}
