package com.al.lightq.config;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Distributed rate limiter using Redis fixed-window counters.
 */
public class RedisRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String RATE_LIMIT_PREFIX = "lightq:rate_limit:";

    private static final Duration KEY_TTL = Duration.ofSeconds(5); // Wait a bit longer than window to handle clock
                                                                   // drift

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if the action is allowed for the given key and limit.
     *
     * @param key   The key (e.g., "push" or "pop")
     * @param limit The maximum number of allowed actions per second
     * @return true if allowed, false if limit exceeded
     */
    public boolean allow(String key, int limit) {
        if (limit <= 0) {
            return true;
        }

        long currentSecond = Instant.now().getEpochSecond();
        String redisKey = RATE_LIMIT_PREFIX + key + ":" + currentSecond;

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, KEY_TTL);
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            logger.error("Error accessing Redis for rate limiting, allowing request fallback", e);
            // Fail open to avoid blocking traffic if Redis is down for rate limiting
            return true;
        }
    }
}
