package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.al.lightq.model.Message;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ViewMessageServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private RedisQueueService redisQueueService;

	@InjectMocks
	private ViewMessageService viewMessageService;

	private String consumerGroup;
	private Message message1;
	private Message message2;

	@BeforeEach
	void setUp() {
		consumerGroup = "testGroup";
		message1 = new Message("id1", consumerGroup, "content1", new Date(100), false);
		message2 = new Message("id2", consumerGroup, "content2", new Date(200), false);
	}

	@Test
	void viewMessages_ConsumedFlagTrue() {
		message1.setConsumed(true);
		when(mongoTemplate.find(any(Query.class), eq(Message.class), eq(consumerGroup)))
				.thenReturn(Arrays.asList(message1));

		List<Message> result = viewMessageService.view(consumerGroup, 10, "yes");

		assertEquals(1, result.size());
		assertEquals(message1, result.get(0));
		verify(redisQueueService, never()).viewMessages(eq(consumerGroup), anyInt());
	}

	@Test
    void viewMessages_CacheHasEnoughUnconsumed() {
        // Mock Cache returning 2 unconsumed messages
        when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(Arrays.asList(message1, message2));

        // Mock consistency check: NONE are consumed in DB
        // find(query) where id in [id1, id2] and consumed=true -> returns empty
        when(mongoTemplate.find(any(Query.class), eq(Message.class), eq(consumerGroup))).thenReturn(new ArrayList<>());

        List<Message> result = viewMessageService.view(consumerGroup, 2, "no");

        assertEquals(2, result.size());
        assertEquals(message1, result.get(0));
        assertEquals(message2, result.get(1));
        verify(redisQueueService, times(1)).viewMessages(eq(consumerGroup), anyInt());
    }

	@Test
    void viewMessages_CacheNotEnough_FetchFromDb() {
        // Cache returns 1
        when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(Arrays.asList(message1));

        // Consistency check: The cached message is valid (not consumed)
        // First find call: logic checks if cached items are consumed (return empty)
        when(mongoTemplate.find(any(Query.class), eq(Message.class), eq(consumerGroup))).thenReturn(new ArrayList<>())
                // Second find call: fetch remaining from DB (excluding cached ID)
                .thenReturn(Arrays.asList(message2));

        // Note: The second `thenReturn` is for the "fill from DB" logic.
        // However, Mockito `find` signature is the same for both calls.
        // logic:
        // 1. consistency check (find ids in list AND consumed=true) -> returns empty []
        // 2. fill DB (find consumed=true OR false, etc) -> returns [message2]

        List<Message> result = viewMessageService.view(consumerGroup, 10, "no");

        assertEquals(2, result.size());
        assertTrue(result.contains(message1));
        assertTrue(result.contains(message2));
    }

	@Test
    void viewMessages_CacheHasGhostMessage_ShouldBeExcluded() {
        // Scenario: Redis has message1 (unconsumed view), but DB says message1 is
        // CONSUMED.

        when(redisQueueService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(Arrays.asList(message1));

        // Consistency check: find(id=id1 AND consumed=true) returns [message1]
        List<Message> ghosts = Arrays.asList(message1);
        // We can't reuse message1 instance easily if we want to set consumed=true but
        // keep redis one as false?
        // Actually the check query returns DB state.
        Message consumedMsg = new Message("id1", consumerGroup, "content1", new Date(100), true);

        // 1. Consistency check call
        when(mongoTemplate.find(any(Query.class), eq(Message.class), eq(consumerGroup)))
                .thenReturn(Arrays.asList(consumedMsg))
                // 2. Fill logic call (returns nothing new)
                .thenReturn(new ArrayList<>());

        List<Message> result = viewMessageService.view(consumerGroup, 10, "no");

        // Expect message1 to be removed from result
        assertEquals(0, result.size());

        // Verify verify validation occurred
        verify(mongoTemplate, times(2)).find(any(Query.class), eq(Message.class), eq(consumerGroup));
        // Verify auto-healing removal
        verify(redisQueueService, times(1)).removeOne(consumerGroup, consumedMsg);
    }

	private void assertTrue(boolean contains) {
		assertEquals(true, contains);
	}
}
