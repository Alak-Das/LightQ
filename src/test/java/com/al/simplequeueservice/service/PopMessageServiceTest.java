package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PopMessageServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private CacheService cacheService;

    @Mock(name = "taskExecutor")
    private Executor taskExecutor;

    @InjectMocks
    private PopMessageService popMessageService;

    private String consumerGroup;
    private Message message;

    @BeforeEach
    void setUp() {
        consumerGroup = "testGroup";
        message = new Message("msg1", consumerGroup, "content", Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant()), false);
    }

    @Test
    void testPop_MessageFoundInCache() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(message);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertTrue(result.isPresent());
        assertEquals(message, result.get());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(taskExecutor, times(1)).execute(any(Runnable.class));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }

    @Test
    void testPop_MessageNotFoundInCacheButFoundInDb() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(null);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString())).thenReturn(message);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertTrue(result.isPresent());
        assertEquals(message, result.get());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(taskExecutor, never()).execute(any(Runnable.class));
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }

    @Test
    void testPop_MessageNotFoundInCacheAndInDb() {
        when(cacheService.popMessage(consumerGroup)).thenReturn(null);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString())).thenReturn(null);

        Optional<Message> result = popMessageService.pop(consumerGroup);

        assertFalse(result.isPresent());
        verify(cacheService, times(1)).popMessage(consumerGroup);
        verify(taskExecutor, never()).execute(any(Runnable.class));
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), anyString());
    }
}
