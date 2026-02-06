package com.al.lightq.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.al.lightq.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingInterceptorTest {

	@Test
	void pushLimitedAfterThreshold() throws Exception {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setPushPerSecond(1);
		properties.setPopPerSecond(1000);

		RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
		RateLimitingInterceptor interceptor = new RateLimitingInterceptor(properties, rateLimiter);

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/queue/push");
		MockHttpServletResponse response = new MockHttpServletResponse();

		// Case 1: Allowed
		when(rateLimiter.allow(eq("push:global"), anyInt())).thenReturn(true);
		assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));

		// Case 2: Blocked
		when(rateLimiter.allow(eq("push:global"), anyInt())).thenReturn(false);
		assertThrows(RateLimitExceededException.class, () -> interceptor.preHandle(request, response, new Object()));
	}

	@Test
	void popLimitedAfterThreshold() throws Exception {
		RateLimitProperties properties = new RateLimitProperties();
		properties.setPushPerSecond(1000);
		properties.setPopPerSecond(1);

		RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
		RateLimitingInterceptor interceptor = new RateLimitingInterceptor(properties, rateLimiter);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/queue/pop");
		MockHttpServletResponse response = new MockHttpServletResponse();

		// Case 1: Allowed
		when(rateLimiter.allow(eq("pop:global"), anyInt())).thenReturn(true);
		assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));

		// Case 2: Blocked
		when(rateLimiter.allow(eq("pop:global"), anyInt())).thenReturn(false);
		assertThrows(RateLimitExceededException.class, () -> interceptor.preHandle(request, response, new Object()));
	}
}
