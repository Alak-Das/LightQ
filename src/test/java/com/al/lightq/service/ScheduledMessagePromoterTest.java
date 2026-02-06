package com.al.lightq.service;

import com.al.lightq.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledMessagePromoterTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RedisQueueService redisQueueService;

    private ScheduledMessagePromoter promoter;

    @BeforeEach
    void setUp() {
        promoter = new ScheduledMessagePromoter(mongoTemplate, redisQueueService);
    }

    @Test
    void promoteScheduledMessages_shouldPromoteMessages_whenFound() {
        // Arrange
        Date scheduledTime = Date.from(Instant.now().minusSeconds(10));
        Message msg1 = new Message("msg1", "content", "group1", new Date(), false, scheduledTime, 0);

        // First call returns msg1, second call returns null (stop loop)
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Message.class)))
                .thenReturn(msg1)
                .thenReturn(null);

        // Act
        promoter.promoteScheduledMessages();

        // Assert
        verify(mongoTemplate, times(2)).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Message.class));

        // Verify addMessage is called with the correct score (scheduledTime)
        verify(redisQueueService).addMessage(eq(msg1), eq((double) scheduledTime.getTime()));
    }

    @Test
    void promoteScheduledMessages_shouldDoNothing_whenNoMessagesFound() {
        // Arrange
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Message.class)))
                .thenReturn(null);

        // Act
        promoter.promoteScheduledMessages();

        // Assert
        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Message.class));
        verify(redisQueueService, never()).addMessage(any(), anyDouble());
    }
}
