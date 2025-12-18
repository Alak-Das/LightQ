package com.al.lightq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

/**
 * Type-safe configuration properties for rate limiting.
 * <p>
 * This class holds all the properties prefixed with {@code rate.limit}.
 * </p>
 */
@Data
@Validated
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
    private int popPerSecond = 10;
}
