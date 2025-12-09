package com.al.simplequeueservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConfigTest {

    private RedisConfig redisConfig;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        redisConfig = new RedisConfig();
        ReflectionTestUtils.setField(redisConfig, "redisCacheTtlMinutes", 60L);
    }

    @Test
    void cacheManagerBean() {
        CacheManager cacheManager = redisConfig.cacheManager(redisConnectionFactory);
        assertNotNull(cacheManager);
        // Further assertions could be made to verify cache configurations if needed
        assertTrue(cacheManager.getCacheNames().isEmpty()); // No specific caches configured yet
    }

    @Test
    void redisTemplateBean() {
        RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(redisConnectionFactory);
        assertNotNull(redisTemplate);
        assertNotNull(redisTemplate.getKeySerializer());
        assertTrue(redisTemplate.getKeySerializer() instanceof StringRedisSerializer);
        assertNotNull(redisTemplate.getValueSerializer());
        assertTrue(redisTemplate.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
        assertNotNull(redisTemplate.getHashKeySerializer());
        assertTrue(redisTemplate.getHashKeySerializer() instanceof StringRedisSerializer);
        assertNotNull(redisTemplate.getHashValueSerializer());
        assertTrue(redisTemplate.getHashValueSerializer() instanceof GenericJackson2JsonRedisSerializer);
    }
}
