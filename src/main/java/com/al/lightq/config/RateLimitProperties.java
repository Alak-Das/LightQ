package com.al.lightq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for rate limiting.
 * <p>
 * This class holds all the properties prefixed with {@code rate.limit}.
 * </p>
 */
@Validated
@Configuration
@ConfigurationProperties(prefix = "rate.limit")
public class RateLimitProperties {
    /**
     * Maximum number of /queue/push requests allowed per second.
     */
    @Min(0)
    private int pushPerSecond = 10;

    /**
     * Maximum number of /queue/pop requests allowed per second.
     */
    @Min(0)
    private int popPerSecond = 20;

    // Explicit constructor, getters and setters (replacing Lombok @Data)
    public RateLimitProperties() {
    }

    public int getPushPerSecond() {
        return pushPerSecond;
    }

    public void setPushPerSecond(int pushPerSecond) {
        this.pushPerSecond = pushPerSecond;
    }

    public int getPopPerSecond() {
        return popPerSecond;
    }

    public void setPopPerSecond(int popPerSecond) {
        this.popPerSecond = popPerSecond;
    }
}
