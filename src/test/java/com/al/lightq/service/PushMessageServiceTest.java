package com.al.lightq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.al.lightq.model.Message;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class PushMessageServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private MongoClient mongoClient;

	@Mock
	private CacheService cacheService;

	@Mock
	private ThreadPoolTaskExecutor taskExecutor;

	@InjectMocks
	private PushMessageService pushMessageService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		ReflectionTestUtils.setField(pushMessageService, "mongoDB", "testDB");
		ReflectionTestUtils.setField(pushMessageService, "expireMinutes", 60L);
		ReflectionTestUtils.setField(pushMessageService, "taskExecutor", taskExecutor);

		// Mock index operations to avoid NPE from mongoTemplate.indexOps(...)
		IndexOperations indexOps = mock(IndexOperations.class);
		when(mongoTemplate.indexOps(anyString())).thenReturn(indexOps);
		when(indexOps.ensureIndex(any(Index.class))).thenReturn("ok");
	}

	@Test
	void push() {
		String consumerGroup = "testGroup";
		String content = "testContent";
		Message messageToPush = new Message("testId", consumerGroup, content);

		// Mock MongoClient chain for TTL index creation

		MongoDatabase mockDB = mock(MongoDatabase.class);
		MongoCollection<Document> mockCollection = mock(MongoCollection.class);
		ListIndexesIterable<Document> mockIterable = mock(ListIndexesIterable.class);

		when(mongoClient.getDatabase(anyString())).thenReturn(mockDB);
		when(mockDB.getCollection(anyString())).thenReturn(mockCollection);
		when(mockCollection.listIndexes()).thenReturn(mockIterable);

		// Create an existing index to satisfy ttlExists check
		List<Document> indexList = new ArrayList<>();
		// The index document needs to reflect the actual structure returned by MongoDB,
		// which includes a confluence 'key' field that is itself a document.
		Document indexDoc = new Document().append("name", "createdAt_1") // Example index name
				.append("key", new Document("createdAt", 1)).append("expireAfterSeconds", 60L * 60L); // Set
																										// expireAfterSeconds
																										// to match the
																										// expected TTL
		indexList.add(indexDoc);
		// Use thenAnswer to fill the list passed to into()
		when(mockIterable.into(any(Collection.class))).thenAnswer(invocation -> {
			Collection<Document> coll = invocation.getArgument(0);
			coll.addAll(indexList);
			return coll;
		});

		when(mongoTemplate.save(any(Message.class), eq(consumerGroup))).thenReturn(messageToPush);
		// Mock addMessage call in CacheService (void method)
		org.mockito.Mockito.doNothing().when(cacheService).addMessage(any(Message.class));
		// Mock the execute method of the taskExecutor
		org.mockito.Mockito.doNothing().when(taskExecutor).execute(any(Runnable.class));

		Message result = pushMessageService.push(messageToPush);
		assertNotNull(result);
		assertEquals(content, result.getContent());
		assertEquals(consumerGroup, result.getConsumerGroup());
	}
}
