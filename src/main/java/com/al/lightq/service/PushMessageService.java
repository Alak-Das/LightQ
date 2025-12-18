package com.al.lightq.service;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.al.lightq.util.LightQConstants.CREATED_AT;

/**
 * Service for pushing messages to the queue. Messages are added to a cache
 * and then asynchronously persisted to MongoDB with a TTL index.
 */
@Service
public class PushMessageService {

    private static final Logger logger = LoggerFactory.getLogger(PushMessageService.class);
    private final MongoClient mongoClient;
    @Value("${spring.data.mongodb.database}")
    private String mongoDB;
    private final MongoTemplate mongoTemplate;
    private final CacheService cacheService;
    private final Executor taskExecutor;
    private final LightQProperties lightQProperties;

    public PushMessageService(MongoClient mongoClient, MongoTemplate mongoTemplate, CacheService cacheService, @Qualifier("taskExecutor") Executor taskExecutor, LightQProperties lightQProperties) {
        this.mongoClient = mongoClient;
        this.mongoTemplate = mongoTemplate;
        this.cacheService = cacheService;
        this.taskExecutor = taskExecutor;
        this.lightQProperties = lightQProperties;
    }

    /**
     * Pushes a message to the queue. The message is first added to a cache for immediate availability,
     * and then saved to MongoDB asynchronously. A TTL index is ensured for the message's collection.
     *
     * @param message The {@link Message} object to be pushed.
     * @return The {@link Message} that was pushed.
     */
    public Message push(Message message) {
        int contentLength = message.getContent() != null ? message.getContent().length() : 0;
        logger.debug("Attempting to push message to Consumer Group: {} with contentLength={} chars", message.getConsumerGroup(), contentLength);
        // Save the Message to Cache
        cacheService.addMessage(message);
        logger.debug("Message with ID {} added to cache for Consumer Group: {}", message.getId(), message.getConsumerGroup());

        // Save the Message to DB Asynchronously
        taskExecutor.execute(() -> {
            try {
                createTTLIndex(message);
                mongoTemplate.save(message, message.getConsumerGroup());
                logger.info("Message with ID {} asynchronously saved to DB for Consumer Group: {}", message.getId(), message.getConsumerGroup());
            } catch (Exception e) {
                logger.error("Failed to persist message asynchronously: messageId={}, consumerGroup={}, error={}", message.getId(), message.getConsumerGroup(), e.getMessage(), e);
            }
        });
        return message;
    }

    /**
     * Ensures a TTL (Time-To-Live) index exists on the 'createdAt' field for the message's collection.
     * This index automatically deletes documents after a specified time.
     *
     * @param message The {@link Message} for which to ensure the TTL index.
     */
    private void createTTLIndex(Message message) {
        MongoCollection<Document> collection = mongoClient.getDatabase(mongoDB).getCollection(message.getConsumerGroup());
        boolean ttlExists = collection.listIndexes()
                .into(new java.util.ArrayList<>())
                .stream()
                .anyMatch(index -> {
                    Document key = (Document) index.get("key");
                    return key != null && key.containsKey(CREATED_AT);
                });

        if (!ttlExists) {
            long expireMinutes = lightQProperties.getPersistenceDurationMinutes();
            logger.info("TTL index does not exist on field: {} for collection: {}. Creating with {} minutes expiration.", CREATED_AT, message.getConsumerGroup(), expireMinutes);
            IndexOptions indexOptions = new IndexOptions().expireAfter(expireMinutes, TimeUnit.MINUTES);
            collection.createIndex(new Document(CREATED_AT, 1), indexOptions);
            logger.info("TTL index created on field: {} for collection: {}", CREATED_AT, message.getConsumerGroup());
        } else {
            logger.debug("TTL index already exists on field: {} for collection: {}", CREATED_AT, message.getConsumerGroup());
        }
    }
}
