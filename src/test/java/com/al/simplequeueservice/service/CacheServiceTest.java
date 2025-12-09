package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import com.al.simplequeueservice.util.SQSConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @InjectMocks
    private CacheService cacheService;

    private static final String CONSUMER_GROUP = "testGroup";
    private static final String CACHE_KEY = SQSConstants.CACHE_PREFIX + CONSUMER_GROUP;
    private Message message;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(cacheService, "redisCacheTtlMinutes", 60L);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        message = new Message("id1", CONSUMER_GROUP, "content1");
    }

    @Test
    void addMessage() {
        cacheService.addMessage(message);

        verify(listOperations, times(1)).leftPush(eq(CACHE_KEY), eq(message));
        verify(redisTemplate, times(1)).expire(eq(CACHE_KEY), any(Duration.class));
    }

    @Test
    void popMessage() {
        when(listOperations.rightPop(eq(CACHE_KEY))).thenReturn(message);

        Message result = cacheService.popMessage(CONSUMER_GROUP);

        assertNotNull(result);
        assertEquals(message.getId(), result.getId());
        assertEquals(message.getContent(), result.getContent());
        verify(listOperations, times(1)).rightPop(eq(CACHE_KEY));
    }

    @Test
    void popMessage_noMessage() {
        when(listOperations.rightPop(eq(CACHE_KEY))).thenReturn(null);

        Message result = cacheService.popMessage(CONSUMER_GROUP);

        assertNull(result);
        verify(listOperations, times(1)).rightPop(eq(CACHE_KEY));
    }

    @Test
    void viewMessages() {
        Message message2 = new Message("id2", CONSUMER_GROUP, "content2");
        List<Object> cachedObjects = Arrays.asList(message, message2);
        when(listOperations.range(eq(CACHE_KEY), eq(0L), eq(-1L))).thenReturn(cachedObjects);

        List<Message> result = cacheService.viewMessages(CONSUMER_GROUP);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(message.getId(), result.get(0).getId());
        assertEquals(message2.getId(), result.get(1).getId());
        verify(listOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(-1L));
    }

    @Test
    void viewMessages_emptyCache() {
        when(listOperations.range(eq(CACHE_KEY), eq(0L), eq(-1L))).thenReturn(Collections.emptyList());

        List<Message> result = cacheService.viewMessages(CONSUMER_GROUP);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(listOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(-1L));
    }

    @Test
    void viewMessages_nullCache() {
        when(listOperations.range(eq(CACHE_KEY), eq(0L), eq(-1L))).thenReturn(null);

        List<Message> result = cacheService.viewMessages(CONSUMER_GROUP);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(listOperations, times(1)).range(eq(CACHE_KEY), eq(0L), eq(-1L));
    }
}
