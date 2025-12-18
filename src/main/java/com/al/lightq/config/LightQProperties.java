package com.al.lightq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lightq")
public class LightQProperties {

    private int messageAllowedToFetch = 50;
    private int persistenceDurationMinutes = 30;
    private int cacheTtlMinutes = 5;

    public int getMessageAllowedToFetch() {
        return messageAllowedToFetch;
    }

    public void setMessageAllowedToFetch(int messageAllowedToFetch) {
        this.messageAllowedToFetch = messageAllowedToFetch;
    }

    public int getPersistenceDurationMinutes() {
        return persistenceDurationMinutes;
    }

    public void setPersistenceDurationMinutes(int persistenceDurationMinutes) {
        this.persistenceDurationMinutes = persistenceDurationMinutes;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }
}
