package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
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
	}

	@Test
    void testPop_MessageFoundInCache() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(message);
        // reservation succeeds in DB
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString()))
                .thenReturn(message);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertTrue(result.isPresent());
        assertEquals(message, result.get());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }

	@Test
    void testPop_MessageNotFoundInCacheButFoundInDb() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(null);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString())).thenReturn(message);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertTrue(result.isPresent());
        assertEquals(message, result.get());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }

	@Test
    void testPop_MessageNotFoundInCacheAndInDb() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(null);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString())).thenReturn(null);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertFalse(result.isPresent());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }
}
