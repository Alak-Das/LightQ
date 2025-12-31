package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
	private CacheService cacheService;

	@InjectMocks
	private PopMessageService popMessageService;

	@Mock
	private LightQProperties lightQProperties;

	private String consumerGroup;
	private Message message;

	@BeforeEach
	void setUp() {
		consumerGroup = "testGroup";
		message = new Message("msg1", consumerGroup, "content",
				Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), false);

		when(lightQProperties.getVisibilityTimeoutSeconds()).thenReturn(30);
		when(lightQProperties.getMaxDeliveryAttempts()).thenReturn(5);
		// Used by PopMessageService to compute cache scan window
		when(lightQProperties.getMessageAllowedToFetch()).thenReturn(50);
	}

	@Test
	void testPop_MessageFoundInCache() {
		// Non-destructive peek returns a candidate
		when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of(message));
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
		verify(cacheService, never()).popMessage(anyString());
		// verify conditional cache removal after successful reservation
		verify(cacheService, times(1)).removeOne(consumerGroup, message);
		// DB reserve attempted
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
	}

	@Test
	void testPop_MessageNotFoundInCacheButFoundInDb() {
		// No cache candidates
		when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of());
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

		verify(cacheService, never()).popMessage(anyString());
		verify(cacheService, never()).removeOne(anyString(), any(Message.class));
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
	}

	@Test
	void testPop_MessageNotFoundInCacheAndInDb() {
		// No cache candidates
		when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(List.of());
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

		verify(cacheService, never()).popMessage(anyString());
		verify(cacheService, never()).removeOne(anyString(), any(Message.class));
		verify(mongoTemplate, times(1))
				.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
	}
}
