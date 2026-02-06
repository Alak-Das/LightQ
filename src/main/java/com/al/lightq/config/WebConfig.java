package com.al.lightq.config;

import com.al.lightq.LightQConstants;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for web-related beans and interceptors.
 * <p>
 * This class enables the {@link RateLimitProperties} and registers the
 * {@link RateLimitingInterceptor}.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebConfig implements WebMvcConfigurer {

	private final RateLimitProperties rateLimitProperties;
	private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	public WebConfig(RateLimitProperties rateLimitProperties,
			org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
		this.rateLimitProperties = rateLimitProperties;
		this.redisTemplate = redisTemplate;
	}

	/**
	 * Creates a rate limiting interceptor.
	 *
	 * @return the rate limiting interceptor
	 */
	@Bean
	public RateLimitingInterceptor rateLimitingInterceptor() {
		return new RateLimitingInterceptor(rateLimitProperties, new RedisRateLimiter(redisTemplate));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(rateLimitingInterceptor()).addPathPatterns(
				LightQConstants.QUEUE_BASE_URL + LightQConstants.PUSH_URL,
				LightQConstants.QUEUE_BASE_URL + LightQConstants.POP_URL);
	}
}
