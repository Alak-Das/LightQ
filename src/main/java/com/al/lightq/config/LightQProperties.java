package com.al.lightq.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the LightQ application.
 * <p>
 * This class holds all the properties prefixed with {@code lightq}.
 * </p>
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "lightq")
public class LightQProperties {

    /**
     * The maximum number of messages to fetch in a single view request.
     */
    @Min(1)
    private int messageAllowedToFetch = 50;

    /**
     * The duration in minutes for which messages are persisted in MongoDB.
     */
    @Min(1)
    private int persistenceDurationMinutes = 30;

    /**
     * The time-to-live in minutes for messages in the Redis cache.
     */
    @Min(1)
    private int cacheTtlMinutes = 5;
}
