package com.al.lightq.config;

import com.al.lightq.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * Configuration for Redis.
 * <p>
 * Provides a RedisConnectionFactory with tuned timeouts and a RedisTemplate for
 * storing Message objects in Redis lists with a hardened ObjectMapper.
 * </p>
 */
@Configuration
public class RedisConfig {

	/**
	 * Lettuce connection factory with configurable command/shutdown timeouts.
	 */
	@Bean
	public LettuceConnectionFactory redisConnectionFactory(LightQProperties props,
			@Value("${spring.data.redis.host:localhost}") String host,
			@Value("${spring.data.redis.port:6379}") int port,
			@Value("${spring.data.redis.password:}") String password) {

		RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
		if (StringUtils.hasText(password)) {
			standalone.setPassword(RedisPassword.of(password));
		}

		LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
				.commandTimeout(Duration.ofSeconds(props.getRedisCommandTimeoutSeconds()))
				.shutdownTimeout(Duration.ofSeconds(props.getRedisShutdownTimeoutSeconds())).build();

		return new LettuceConnectionFactory(standalone, clientConfig);
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

		// Configure a safe ObjectMapper (no default typing) and ISO-8601 dates
		ObjectMapper om = new ObjectMapper();
		om.findAndRegisterModules();
		om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		Jackson2JsonRedisSerializer<Message> valueSerializer = new Jackson2JsonRedisSerializer<>(Message.class);
		valueSerializer.setObjectMapper(om);

		template.setKeySerializer(keySerializer);
		template.setValueSerializer(valueSerializer);
		template.setHashKeySerializer(keySerializer);
		template.setHashValueSerializer(valueSerializer);
		template.afterPropertiesSet();
		return template;
	}
}
