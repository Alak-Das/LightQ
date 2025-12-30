package com.al.lightq.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.al.lightq.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisConfigTest {

	private RedisConfig redisConfig;

	@Mock
	private RedisConnectionFactory redisConnectionFactory;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		redisConfig = new RedisConfig();
	}

	@Test
	void redisTemplateBean() {
		RedisTemplate<String, Message> redisTemplate = redisConfig.redisTemplate(redisConnectionFactory);
		assertNotNull(redisTemplate);
		assertNotNull(redisTemplate.getKeySerializer());
		assertTrue(redisTemplate.getKeySerializer() instanceof StringRedisSerializer);
		assertNotNull(redisTemplate.getValueSerializer());
		assertTrue(redisTemplate.getValueSerializer() instanceof RedisConfig.SmileMessageRedisSerializer);
		assertNotNull(redisTemplate.getHashKeySerializer());
		assertTrue(redisTemplate.getHashKeySerializer() instanceof StringRedisSerializer);
		assertNotNull(redisTemplate.getHashValueSerializer());
		assertTrue(redisTemplate.getHashValueSerializer() instanceof RedisConfig.SmileMessageRedisSerializer);
	}
}
