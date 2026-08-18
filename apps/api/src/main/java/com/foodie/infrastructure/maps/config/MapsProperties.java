package com.foodie.infrastructure.maps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "foodie.maps")
public class MapsProperties {

    private String provider = "google";
    private String apiKey = "";
    private String baseUrl = "https://maps.googleapis.com/maps/api";
    private int timeoutMs = 5000;
    private int cacheTtlHours = 24;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getCacheTtlHours() {
        return cacheTtlHours;
    }

    public void setCacheTtlHours(int cacheTtlHours) {
        this.cacheTtlHours = cacheTtlHours;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"stub".equalsIgnoreCase(apiKey);
    }
}
