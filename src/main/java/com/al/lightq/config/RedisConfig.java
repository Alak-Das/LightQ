package com.al.lightq.config;

import com.al.lightq.model.Message;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis.
 * <p>
 * This class sets up the Redis cache manager and template.
 * </p>
 */
@Configuration
public class RedisConfig {

	private final LightQProperties lightQProperties;

	public RedisConfig(LightQProperties lightQProperties) {
		this.lightQProperties = lightQProperties;
	}

	/**
	 * Creates a Redis cache manager.
	 *
	 * @param redisConnectionFactory
	 *            the Redis connection factory
	 * @return the cache manager
	 */
	@Bean
	public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
		RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofMinutes(lightQProperties.getCacheTtlMinutes()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer()));

		return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(cacheConfiguration).build();
	}

	/**
	 * Creates a Redis template.
	 *
	 * @param connectionFactory
	 *            the Redis connection factory
	 * @return the Redis template
	 */
	@Bean
	public RedisTemplate<String, Message> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Message> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		StringRedisSerializer keySerializer = new StringRedisSerializer();
		Jackson2JsonRedisSerializer<Message> valueSerializer = new Jackson2JsonRedisSerializer<>(Message.class);
		template.setKeySerializer(keySerializer);
		template.setValueSerializer(valueSerializer);
		template.setHashKeySerializer(keySerializer);
		template.setHashValueSerializer(valueSerializer);
		template.afterPropertiesSet();
		return template;
	}

}
