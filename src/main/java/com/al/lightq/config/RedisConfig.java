package com.al.lightq.config;

import com.al.lightq.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
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

		GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
		poolConfig.setMaxTotal(props.getRedisPoolMaxTotal());
		poolConfig.setMaxIdle(props.getRedisPoolMaxIdle());
		poolConfig.setMinIdle(props.getRedisPoolMinIdle());

		LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
				.commandTimeout(Duration.ofSeconds(props.getRedisCommandTimeoutSeconds()))
				.shutdownTimeout(Duration.ofSeconds(props.getRedisShutdownTimeoutSeconds())).poolConfig(poolConfig)
				.build();

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
		ObjectMapper om = new ObjectMapper(new SmileFactory());
		om.findAndRegisterModules();
		om.registerModule(new AfterburnerModule());
		om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		RedisSerializer<Message> valueSerializer = new SmileMessageRedisSerializer(om);

		template.setKeySerializer(keySerializer);
		template.setValueSerializer(valueSerializer);
		template.setHashKeySerializer(keySerializer);
		template.setHashValueSerializer(valueSerializer);
		template.afterPropertiesSet();
		return template;
	}

	static final class SmileMessageRedisSerializer implements RedisSerializer<Message> {
		private final ObjectMapper mapper;

		SmileMessageRedisSerializer(ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public byte[] serialize(Message message) throws SerializationException {
			try {
				if (message == null) {
					return null;
				}
				return mapper.writeValueAsBytes(message);
			} catch (Exception e) {
				throw new SerializationException("Smile serialize failed", e);
			}
		}

		@Override
		public Message deserialize(byte[] bytes) throws SerializationException {
			try {
				if (bytes == null || bytes.length == 0) {
					return null;
				}
				return mapper.readValue(bytes, Message.class);
			} catch (Exception e) {
				throw new SerializationException("Smile deserialize failed", e);
			}
		}
	}
}
