package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
public class PopMessageServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private RedisQueueService redisQueueService;

	private PopMessageService popMessageService;

	@Mock
	private DlqService dlqService;

	@Mock
	private LightQProperties lightQProperties;

	private SimpleMeterRegistry meterRegistry;

	private String consumerGroup;
	private Message message;

	@BeforeEach
	void setUp() {
		consumerGroup = "testGroup";
		message = new Message("msg1", consumerGroup, "content",
				Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), false);

		meterRegistry = new SimpleMeterRegistry();

		when(lightQProperties.getVisibilityTimeoutSeconds()).thenReturn(30);
		when(lightQProperties.getMaxDeliveryAttempts()).thenReturn(5);
		// Used by PopMessageService to compute cache scan window
		when(lightQProperties.getMessageAllowedToFetch()).thenReturn(50);

		popMessageService = new PopMessageService(mongoTemplate, redisQueueService, lightQProperties, dlqService,
				meterRegistry);
	}

	@Test
	void testPop_MessageFoundInCache() {
		// Non-destructive peek returns a candidate
		when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of(message));
		// Reservation succeeds in DB
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(Message.class),
				anyString()))
				.thenReturn(message);

		Optional<Message> result = popMessageService.pop(consumerGroup);

		assertTrue(result.isPresent());
		assertEquals(message, result.get());

		// verify we did not use destructive pop()
		verify(redisQueueService, never()).popMessage(anyString());
		// verify conditional cache removal after successful reservation
		verify(redisQueueService, times(1)).removeOne(consumerGroup, message);
		// DB reserve attempted
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class),
						anyString());

		// Verify metrics
		assertEquals(1.0, meterRegistry.get("lightq.messages.popped.total").tag("source", "cache").counter().count());
		assertNotNull(meterRegistry.find("lightq.pop.latency").timer());
	}

	@Test
	void testPop_MessageNotFoundInCacheButFoundInDb() {
		// No cache candidates
		when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of());
		// Oldest-available DB reservation succeeds
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(Message.class),
				anyString()))
				.thenReturn(message);

		Optional<Message> result = popMessageService.pop(consumerGroup);

		assertTrue(result.isPresent());
		assertEquals(message, result.get());

		verify(redisQueueService, never()).popMessage(anyString());
		verify(redisQueueService, never()).removeOne(anyString(), any(Message.class));
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class),
						anyString());

		// Verify metrics
		assertEquals(1.0, meterRegistry.get("lightq.messages.popped.total").tag("source", "db").counter().count());
	}

	@Test
	void testPop_MessageNotFoundInCacheAndInDb() {
		// No cache candidates
		when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of());
		// DB reservation returns nothing
		when(mongoTemplate.findAndModify(
				any(Query.class),
				any(Update.class),
				any(FindAndModifyOptions.class),
				eq(Message.class),
				anyString()))
				.thenReturn(null);

		Optional<Message> result = popMessageService.pop(consumerGroup);

		assertFalse(result.isPresent());

		verify(redisQueueService, never()).popMessage(anyString());
		verify(redisQueueService, never()).removeOne(anyString(), any(Message.class));
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class),
						anyString());

		// Verify timer recorded even on empty
		assertNotNull(meterRegistry.find("lightq.pop.latency").timer());
	}

	@Test
	void testPop_MaxDeliveryAttempts_ShouldMoveToDlq() {
		// Mock message with high delivery count
		Message failingMessage = new Message("fail-id", consumerGroup, "fail", new Date(), false);
		failingMessage.setDeliveryCount(6); // > 5

		// No cache candidates
		when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of());
		// First reserveOldestAvailable call returns the failing message
		// Second reserveOldestAvailable call returns nothing (simulating empty queue
		// after DLQ move)
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Message.class), anyString())).thenReturn(failingMessage).thenReturn(null);

		Optional<Message> result = popMessageService.pop(consumerGroup);

		assertFalse(result.isPresent());

		// Verify DLQ service called
		verify(dlqService, times(1)).moveToDlq(eq(failingMessage), eq(consumerGroup), anyString());
		// Verify we tried to fetch from DB twice (once got failing, then empty)
		verify(mongoTemplate, times(2)).findAndModify(any(Query.class), any(Update.class),
				any(FindAndModifyOptions.class), eq(Message.class), anyString());
	}

	@Test
	void testPop_SelfHealing_RemovesInvalidMessageFromCache() {
		// Scenario: Message in cache, but marked consumed in DB
		Message invalidMessage = new Message("invalid-id", consumerGroup, "content");
		invalidMessage.setConsumed(true); // in reality, the DB instance returns true

		// Cache return it
		when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of(invalidMessage));

		// Reservation fails (returns null)
		when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
				eq(Message.class), anyString())).thenReturn(null);

		// Self-healing check: findById returns the consumed message
		Message consumedInDb = new Message("invalid-id", consumerGroup, "content");
		consumedInDb.setConsumed(true);
		when(mongoTemplate.findById(eq("invalid-id"), eq(Message.class), eq(consumerGroup))).thenReturn(consumedInDb);

		// Fallback to empty DB (so loop finishes)
		// Note: The second findAndModify is for reserveOldestAvailable
		// We mock it to return null to end the loop
		// But verify invocation count correctly.
		// reserveById (1) -> null
		// reserveOldestAvailable (1) -> null
		// So total findAndModify = 2

		Optional<Message> result = popMessageService.pop(consumerGroup);

		assertFalse(result.isPresent());

		// Verify removeOne was called due to self-healing
		verify(redisQueueService, times(1)).removeOne(consumerGroup, invalidMessage);
	}
}
