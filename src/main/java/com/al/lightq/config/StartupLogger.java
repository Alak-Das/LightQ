package com.al.lightq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Logs key runtime configuration and limits at startup for operational visibility.
 * Avoids logging secrets (no credentials or full URIs).
 */
@Component
public class StartupLogger implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StartupLogger.class);

    private final RateLimitProperties rateLimitProperties;

    public StartupLogger(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${cache.ttl.minutes}")
    private long cacheTtlMinutes;

    @Value("${spring.data.mongodb.database}")
    private String mongoDb;

    @Value("${persistence.duration.minutes}")
    private long persistenceMinutes;

    @Value("${no.of.message.allowed.to.fetch}")
    private long messageAllowedCount;

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Startup configuration: rateLimits pushPerSec={}, popPerSec={}",
                rateLimitProperties.getPushPerSecond(), rateLimitProperties.getPopPerSecond());
        logger.info("Startup configuration: messageAllowedCount={}", messageAllowedCount);
        logger.info("Startup configuration: redis host={}, port={}, ttlMinutes={}",
                redisHost, redisPort, cacheTtlMinutes);
        logger.info("Startup configuration: mongo database={}, persistenceTTLMinutes={}",
                mongoDb, persistenceMinutes);
    }
}
