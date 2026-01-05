package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.test.util.ReflectionTestUtils;

class PushMessageServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private CacheService cacheService;

	@Mock
	private LightQProperties lightQProperties;

	@InjectMocks
	private PushMessageService pushMessageService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(pushMessageService, "expireMinutes", 60L);

		// Mock index operations to avoid NPE from mongoTemplate.indexOps(...)
		IndexOperations indexOps = mock(IndexOperations.class);
		when(mongoTemplate.indexOps(anyString())).thenReturn(indexOps);
		when(indexOps.ensureIndex(any(Index.class))).thenReturn("ok");
		when(lightQProperties.getPersistenceDurationMinutes()).thenReturn(60);
	}

	@Test
	void push() {
		String consumerGroup = "testGroup";
		String content = "testContent";
		Message messageToPush = new Message("testId", consumerGroup, content);

		// Mock addMessage call in CacheService (void method)
		org.mockito.Mockito.doNothing().when(cacheService).addMessage(any(Message.class));
		// Mock insert into MongoTemplate (non-void)
		when(mongoTemplate.insert(any(Message.class), eq(consumerGroup))).thenReturn(messageToPush);

		Message result = pushMessageService.push(messageToPush);
		assertNotNull(result);
		assertEquals(content, result.getContent());
		assertEquals(consumerGroup, result.getConsumerGroup());
		org.mockito.Mockito.verify(cacheService, org.mockito.Mockito.times(1)).addMessage(messageToPush);
	}

	@Test
	void pushScheduled_shouldSkipCache() {
		String consumerGroup = "testGroup";
		String content = "testContent";
		java.util.Date future = new java.util.Date(System.currentTimeMillis() + 10000);
		Message messageToPush = new Message("testId", consumerGroup, content, future);

		// Mock insert into MongoTemplate (non-void)
		when(mongoTemplate.insert(any(Message.class), eq(consumerGroup))).thenReturn(messageToPush);

		Message result = pushMessageService.push(messageToPush);
		assertNotNull(result);

		// Verify verify cache was skipped
		org.mockito.Mockito.verify(cacheService, org.mockito.Mockito.never()).addMessage(any(Message.class));
		// Verify DB persist was called
		org.mockito.Mockito.verify(mongoTemplate, org.mockito.Mockito.atLeastOnce()).insert(any(Message.class),
				eq(consumerGroup));
	}
}
