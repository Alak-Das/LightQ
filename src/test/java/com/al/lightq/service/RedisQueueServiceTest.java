package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.al.lightq.LightQConstants;
import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.test.util.ReflectionTestUtils;

class RedisQueueServiceTest {

	@Mock
	private RedisTemplate<String, Message> redisTemplate;

	@Mock
	private LightQProperties lightQProperties;

	@Mock
	private ZSetOperations<String, Message> zSetOperations;

	// Use real SimpleMeterRegistry for metrics testing
	private SimpleMeterRegistry meterRegistry;

	private RedisQueueService redisQueueService;

	private static final String CONSUMER_GROUP = "testGroup";
	private static final String CACHE_KEY = LightQConstants.CACHE_PREFIX + CONSUMER_GROUP;
	private Message message;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		meterRegistry = new SimpleMeterRegistry();

		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(lightQProperties.getCacheTtlMinutes()).thenReturn(30);
		when(lightQProperties.getCacheMaxEntriesPerGroup()).thenReturn(100);

		// Mock CircuitBreaker
		io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry registry = mock(
				io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.class);
		io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker = mock(
				io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
		when(registry.circuitBreaker("redis")).thenReturn(circuitBreaker);

		// Mock execution methods
		doAnswer(inv -> {
			Runnable r = inv.getArgument(0);
			r.run();
			return null;
		}).when(circuitBreaker).executeRunnable(any(Runnable.class));

		doAnswer(inv -> {
			java.util.function.Supplier<?> s = inv.getArgument(0);
			return s.get();
		}).when(circuitBreaker).executeSupplier(any(java.util.function.Supplier.class));

		redisQueueService = new RedisQueueService(redisTemplate, lightQProperties, meterRegistry, registry);
		ReflectionTestUtils.setField(redisQueueService, "redisCacheTtlMinutes", 60L);

		message = new Message("id1", CONSUMER_GROUP, "content1");
	}

	@Test
	void addMessage() {
		when(redisTemplate.executePipelined(any(org.springframework.data.redis.core.SessionCallback.class)))
				.thenReturn(java.util.Collections.emptyList());

		redisQueueService.addMessage(message);

		verify(redisTemplate, times(1))
				.executePipelined(any(org.springframework.data.redis.core.SessionCallback.class));

		// Verify gauge was created
		assertNotNull(meterRegistry.find("lightq.queue.depth").gauge());
	}

	@Test
	void popMessage() {
		TypedTuple<Message> tuple = new DefaultTypedTuple<>(message, 1000.0);
		when(zSetOperations.popMin(eq(CACHE_KEY))).thenReturn(tuple);

		Message result = redisQueueService.popMessage(CONSUMER_GROUP);

		assertNotNull(result);
		assertEquals(message.getId(), result.getId());
		verify(zSetOperations, times(1)).popMin(eq(CACHE_KEY));

		// Verify gauge was created
		assertNotNull(meterRegistry.find("lightq.queue.depth").gauge());
	}

	@Test
	void popMessage_noMessage() {
		when(zSetOperations.popMin(eq(CACHE_KEY))).thenReturn(null);

		Message result = redisQueueService.popMessage(CONSUMER_GROUP);

		assertNull(result);
		verify(zSetOperations, times(1)).popMin(eq(CACHE_KEY));
	}

	@Test
	void viewMessages() {
		Message message2 = new Message("id2", CONSUMER_GROUP, "content2");
		Set<Message> cachedObjects = new java.util.LinkedHashSet<>(Arrays.asList(message, message2));
		// limit=10 -> range(0, 9)
		when(zSetOperations.range(eq(CACHE_KEY), eq(0L), eq(9L))).thenReturn(cachedObjects);

		List<Message> result = redisQueueService.viewMessages(CONSUMER_GROUP, 10);

		assertNotNull(result);
		assertEquals(2, result.size());
		assertTrue(result.contains(message));
		assertTrue(result.contains(message2));
		verify(zSetOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(9L));

		// Verify gauge was created
		assertNotNull(meterRegistry.find("lightq.queue.depth").gauge());
	}

	@Test
	void viewMessages_All() {
		// viewMessages(group) calls range(0, -1)
		Message message2 = new Message("id2", CONSUMER_GROUP, "content2");
		Set<Message> cachedObjects = new java.util.LinkedHashSet<>(Arrays.asList(message, message2));
		when(zSetOperations.range(eq(CACHE_KEY), eq(0L), eq(-1L))).thenReturn(cachedObjects);

		List<Message> result = redisQueueService.viewMessages(CONSUMER_GROUP);

		assertNotNull(result);
		assertEquals(2, result.size());
		verify(zSetOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(-1L));

		// Verify gauge was created
		assertNotNull(meterRegistry.find("lightq.queue.depth").gauge());
	}

	@Test
	void viewMessages_emptyCache() {
		when(zSetOperations.range(eq(CACHE_KEY), eq(0L), eq(9L))).thenReturn(Collections.emptySet());

		List<Message> result = redisQueueService.viewMessages(CONSUMER_GROUP, 10);

		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(zSetOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(9L));
	}

	@Test
	void removeOne() {
		// mock remove return 1
		when(zSetOperations.remove(eq(CACHE_KEY), eq(message))).thenReturn(1L);

		boolean result = redisQueueService.removeOne(CONSUMER_GROUP, message);

		assertTrue(result);
		verify(zSetOperations, times(1)).remove(eq(CACHE_KEY), eq(message));
	}
}
