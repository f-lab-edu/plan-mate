package com.planmate.itinerary.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.itinerary")
public class AiItineraryProperties {

    public static final String PROVIDER_GEMINI_MAPS_GROUNDING = "gemini-maps-grounding";

    private boolean enabled = true;
    private String provider = PROVIDER_GEMINI_MAPS_GROUNDING;
    private String model = "gemini-3.5-flash";
    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private Duration timeout = Duration.ofSeconds(60);
    private double temperature = 0.2;
    private String schemaVersion = "grounded-itinerary-draft-v1";
    private int maxItemsPerDay = 8;
    private int maxRepeatPerPlace = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = normalize(provider, PROVIDER_GEMINI_MAPS_GROUNDING);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = normalize(model, "gemini-3.5-flash");
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = normalize(baseUrl, "https://generativelanguage.googleapis.com/v1beta");
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(60)
                : timeout;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(1.0, temperature));
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = normalize(schemaVersion, "grounded-itinerary-draft-v1");
    }

    public int getMaxItemsPerDay() {
        return maxItemsPerDay;
    }

    public void setMaxItemsPerDay(int maxItemsPerDay) {
        this.maxItemsPerDay = Math.max(1, maxItemsPerDay);
    }

    public int getMaxRepeatPerPlace() {
        return maxRepeatPerPlace;
    }

    public void setMaxRepeatPerPlace(int maxRepeatPerPlace) {
        this.maxRepeatPerPlace = Math.max(1, maxRepeatPerPlace);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
