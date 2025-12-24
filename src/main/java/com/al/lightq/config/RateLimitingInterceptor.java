package com.al.lightq.config;

import com.al.lightq.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Simple fixed-window per-second rate limiter for specific endpoints.
 * <p>
 * Limits are configured via {@link RateLimitProperties}.
 * </p>
 */
public class RateLimitingInterceptor implements HandlerInterceptor {

	private final RateLimitProperties properties;
	private final ConcurrentHashMap<String, RateCounter> counters = new ConcurrentHashMap<>();
	private static final Logger logger = LoggerFactory.getLogger(RateLimitingInterceptor.class);

	public RateLimitingInterceptor(RateLimitProperties properties) {
		this.properties = properties;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String uri = request.getRequestURI();

		if (uri.endsWith("/queue/push")) {
			if (!allow("push", properties.getPushPerSecond())) {
				logger.warn("Rate limit exceeded: key={}, uri={}, limitPerSecond={}", "push", uri,
						properties.getPushPerSecond());
				throw new RateLimitExceededException("Rate limit exceeded for /queue/push");
			}
		} else if (uri.endsWith("/queue/pop")) {
			if (!allow("pop", properties.getPopPerSecond())) {
				logger.warn("Rate limit exceeded: key={}, uri={}, limitPerSecond={}", "pop", uri,
						properties.getPopPerSecond());
				throw new RateLimitExceededException("Rate limit exceeded for /queue/pop");
			}
		}
		return true;
	}

	/**
	 * Checks if a request is allowed for a given key and limit.
	 *
	 * @param key
	 *            the key to check
	 * @param limitPerSecond
	 *            the limit per second
	 * @return true if the request is allowed, false otherwise
	 */
	private boolean allow(String key, int limitPerSecond) {
		if (limitPerSecond <= 0) {
			// non-positive value disables limiting
			return true;
		}
		RateCounter counter = counters.computeIfAbsent(key, k -> new RateCounter());
		return counter.allow(limitPerSecond);
	}

	/**
	 * A simple rate counter.
	 */
	private static class RateCounter {
		private volatile long windowSecond = -1L;
		private final AtomicInteger count = new AtomicInteger(0);

		/**
		 * Checks if a request is allowed for a given limit.
		 *
		 * @param limit
		 *            the limit
		 * @return true if the request is allowed, false otherwise
		 */
		// synchronize to ensure atomic reset + increment across threads
		synchronized boolean allow(int limit) {
			long nowSec = Instant.now().getEpochSecond();
			if (windowSecond != nowSec) {
				windowSecond = nowSec;
				count.set(0);
			}
			int current = count.incrementAndGet();
			return current <= limit;
		}
	}
}
