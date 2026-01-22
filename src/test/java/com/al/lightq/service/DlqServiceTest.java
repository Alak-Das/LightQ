package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

class DlqServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RedisQueueService redisQueueService;

    @Mock
    private LightQProperties lightQProperties;

    private SimpleMeterRegistry meterRegistry;

    private DlqService dlqService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        meterRegistry = new SimpleMeterRegistry();

        when(lightQProperties.getIndexCacheMaxGroups()).thenReturn(100);
        when(lightQProperties.getIndexCacheExpireMinutes()).thenReturn(60);

        dlqService = new DlqService(mongoTemplate, redisQueueService, lightQProperties, meterRegistry);
    }

    @Test
    void ensureDlqIndexes_ShouldUseCache() {
        String consumerGroup = "testGroup";
        String reason = "testReason";
        String dlqSuffix = "-dlq";

        Message message = new Message("id", consumerGroup, "content");

        when(lightQProperties.getDlqSuffix()).thenReturn(dlqSuffix);
        when(lightQProperties.getDlqTtlMinutes()).thenReturn(60); // TTL enabled

        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOps);

        // First call
        dlqService.moveToDlq(message, consumerGroup, reason);

        // Second call
        dlqService.moveToDlq(message, consumerGroup, reason);

        // Verify indexOps.createIndex was called ONLY ONCE due to caching
        verify(indexOps, times(1)).createIndex(any(Index.class));
        // Verify metrics
        assertEquals(2.0, meterRegistry.get("lightq.messages.dlq.total").counter().count());
    }

    @Test
    void ensureDlqIndexes_NoTtl_ShouldSkip() {
        String consumerGroup = "testGroupCtx";
        String reason = "ctx";
        String dlqSuffix = "-dlq";

        Message message = new Message("id2", consumerGroup, "content");

        when(lightQProperties.getDlqSuffix()).thenReturn(dlqSuffix);
        when(lightQProperties.getDlqTtlMinutes()).thenReturn(null); // TTL disabled

        dlqService.moveToDlq(message, consumerGroup, reason);

        // Verify indexOps never requested
        verify(mongoTemplate, never()).indexOps(anyString());
    }
}
