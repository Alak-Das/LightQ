package com.al.lightq.config;

import com.al.lightq.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Distributed rate limiter interceptor requiring Redis.
 * <p>
 * Limits are configured via {@link RateLimitProperties}.
 * </p>
 */
public class RateLimitingInterceptor implements HandlerInterceptor {

	private final RateLimitProperties properties;
	private final RedisRateLimiter rateLimiter;
	private static final Logger logger = LoggerFactory.getLogger(RateLimitingInterceptor.class);

	public RateLimitingInterceptor(RateLimitProperties properties, RedisRateLimiter rateLimiter) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String uri = request.getRequestURI();
		// Determine user and limit
		String userKey = "global";
		Integer userLimit = null;

		org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
				.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated()
				&& auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
			userKey = ((org.springframework.security.core.userdetails.UserDetails) auth.getPrincipal()).getUsername();
			if (auth.getPrincipal() instanceof com.al.lightq.model.LightQUserDetails) {
				userLimit = ((com.al.lightq.model.LightQUserDetails) auth.getPrincipal()).getRateLimit();
			}
		}

		if (uri.endsWith("/queue/push")) {
			int limit = (userLimit != null && userLimit > 0) ? userLimit : properties.getPushPerSecond();
			String key = "push:" + userKey;
			if (!rateLimiter.allow(key, limit)) {
				logger.warn("Rate limit exceeded: key={}, uri={}, limitPerSecond={}", key, uri, limit);
				throw new RateLimitExceededException("Rate limit exceeded for /queue/push");
			}
		} else if (uri.endsWith("/queue/pop")) {
			int limit = (userLimit != null && userLimit > 0) ? userLimit : properties.getPopPerSecond();
			String key = "pop:" + userKey;
			if (!rateLimiter.allow(key, limit)) {
				logger.warn("Rate limit exceeded: key={}, uri={}, limitPerSecond={}", key, uri, limit);
				throw new RateLimitExceededException("Rate limit exceeded for /queue/pop");
			}
		}
		return true;
	}
}
