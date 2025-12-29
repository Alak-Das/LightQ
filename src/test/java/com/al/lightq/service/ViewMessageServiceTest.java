
package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.al.lightq.model.Message;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
public class ViewMessageServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private CacheService cacheService;

	@InjectMocks
	private ViewMessageService viewMessageService;

	private String consumerGroup;
	private int messageCount;
	private Message message1;
	private Message message2;
	private Message message3;

	@BeforeEach
	void setUp() {
		consumerGroup = "testGroup";
		messageCount = 10;
		message1 = new Message("id1", consumerGroup, "content1",
				Date.from(LocalDateTime.now().minusHours(3).atZone(ZoneId.systemDefault()).toInstant()), false);
		message2 = new Message("id2", consumerGroup, "content2",
				Date.from(LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault()).toInstant()), true);
		message3 = new Message("id3", consumerGroup, "content3",
				Date.from(LocalDateTime.now().minusHours(1).atZone(ZoneId.systemDefault()).toInstant()), false);
	}

	@Test
    void testView_noConsumedFilter() {
        // When consumed is null, cache is consulted first, then DB tops up excluding cache IDs.
        when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(new ArrayList<>());
        when(mongoTemplate.find(any(Query.class), eq(Message.class), anyString())).thenReturn(Arrays.asList(message2, message3));

        List<Message> result = viewMessageService.view(consumerGroup, messageCount, null);

        assertEquals(2, result.size());
        assertTrue(result.containsAll(Arrays.asList(message2, message3)));
        verify(cacheService, times(1)).viewMessages(eq(consumerGroup), anyInt());
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Message.class), anyString());
    }

	@Test
    void testView_consumedYesFilter() {
        when(mongoTemplate.find(any(Query.class), eq(Message.class), anyString())).thenReturn(Arrays.asList(message2));

        List<Message> result = viewMessageService.view(consumerGroup, messageCount, "yes");

        assertEquals(1, result.size());
        assertTrue(result.contains(message2));
        verify(cacheService, never()).viewMessages(eq(consumerGroup), anyInt());
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Message.class), anyString());
    }

	@Test
    void testView_consumedNoFilter_cacheAndDb() {
        when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(Arrays.asList(message1));
        when(mongoTemplate.find(any(Query.class), eq(Message.class), anyString())).thenReturn(Arrays.asList(message3));

        List<Message> result = viewMessageService.view(consumerGroup, messageCount, "no");

        assertEquals(2, result.size());
        assertTrue(result.containsAll(Arrays.asList(message1, message3)));
        verify(cacheService, times(1)).viewMessages(eq(consumerGroup), anyInt());
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Message.class), anyString());
    }

	@Test
	void testView_consumedNoFilter_onlyCache() {
		messageCount = 1;
        when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(Arrays.asList(message1, message3));

		List<Message> result = viewMessageService.view(consumerGroup, messageCount, "no");

		assertEquals(1, result.size());
		assertTrue(result.contains(message1));
		verify(cacheService, times(1)).viewMessages(eq(consumerGroup), anyInt());
		verify(mongoTemplate, never()).find(any(Query.class), eq(Message.class), anyString());
	}

	@Test
    void testView_emptyResult() {
        // When consumed is null, cache is checked first; if both cache and DB are empty, result is empty.
        when(cacheService.viewMessages(eq(consumerGroup), anyInt())).thenReturn(new ArrayList<>());
        when(mongoTemplate.find(any(Query.class), eq(Message.class), anyString())).thenReturn(new ArrayList<>());

        List<Message> result = viewMessageService.view(consumerGroup, messageCount, null);

        assertTrue(result.isEmpty());
        verify(cacheService, times(1)).viewMessages(eq(consumerGroup), anyInt());
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Message.class), anyString());
    }
}
