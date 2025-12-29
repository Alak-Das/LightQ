package com.al.lightq.config;

import com.al.lightq.model.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis.
 * <p>
 * Provides a RedisTemplate for storing Message objects in Redis lists.
 * </p>
 */
@Configuration
public class RedisConfig {



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
