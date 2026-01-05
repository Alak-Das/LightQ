package com.al.lightq.config;

import com.al.lightq.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
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
	/**
	 * A simple rate counter using lock-free CAS.
	 */
	private static class RateCounter {
		// Encapsulate state in an immutable object for atomic swapping
		private static class Window {
			final long second;
			final int count;

			Window(long second, int count) {
				this.second = second;
				this.count = count;
			}
		}

		private final java.util.concurrent.atomic.AtomicReference<Window> state = new java.util.concurrent.atomic.AtomicReference<>(
				new Window(0, 0));

		/**
		 * Checks if a request is allowed for a given limit.
		 *
		 * @param limit
		 *            the limit
		 * @return true if the request is allowed, false otherwise
		 */
		boolean allow(int limit) {
			long nowSec = Instant.now().getEpochSecond();

			while (true) {
				Window current = state.get();
				Window next;

				if (current.second != nowSec) {
					// New second window, try to reset to count=1
					next = new Window(nowSec, 1);
				} else {
					// Same second, increment count
					if (current.count >= limit) {
						return false; // Limit exceeded
					}
					next = new Window(nowSec, current.count + 1);
				}

				// CAS: if state is still 'current', swap with 'next'
				if (state.compareAndSet(current, next)) {
					return true;
				}
				// If CAS failed, loop and retry with fresh state
			}
		}
	}
}
