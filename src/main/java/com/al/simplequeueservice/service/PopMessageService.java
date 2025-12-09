package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;

import static com.al.simplequeueservice.util.SQSConstants.*;

/**
 * Service for popping messages from the queue, prioritizing cache and then falling back to the database.
 * Messages popped from the cache are asynchronously marked as consumed in the database.
 */
@Service
public class PopMessageService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    private static final Logger logger = LoggerFactory.getLogger(PopMessageService.class);

    /**
     * Pops the oldest available message for a given consumer group.
     * It first tries to fetch the message from the cache. If not found, it fetches from the database.
     * Messages fetched from cache are asynchronously marked as consumed in the database.
     *
     * @param consumerGroup The consumer group from which to pop the message.
     * @return An {@link Optional} containing the message if found, or empty if no message is available.
     */
    public Optional<Message> pop(String consumerGroup) {
        logger.debug("Attempting to pop oldest message from the queue for Consumer Group: {}", consumerGroup);
        // Get from Cache
        Message cachedMessage = cacheService.popMessage(consumerGroup);
        if (cachedMessage != null) {
            logger.debug("Message with ID {} found in cache for Consumer Group: {}. Asynchronously updating status in DB.", cachedMessage.getId(), consumerGroup);
            taskExecutor.execute(() -> {
                updateMessageInMongo(cachedMessage.getId(), consumerGroup);
            });
            return Optional.of(cachedMessage);
        }
        // Get from DB if not in Cache
        logger.debug("Message not found in cache for Consumer Group: {}. Fetching from DB.", consumerGroup);
        Query query = new Query(Criteria.where(CONSUMED).is(false))
                .with(Sort.by(Sort.Direction.ASC, CREATED_AT));
        Update update = new Update().set(CONSUMED, true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        if (message != null) {
            logger.info("Message with ID {} popped from DB for Consumer Group: {}", message.getId(), consumerGroup);
        } else {
            logger.debug("No unconsumed message found in DB for Consumer Group: {}", consumerGroup);
        }
        return Optional.ofNullable(message);
    }

    /**
     * Asynchronously updates the 'consumed' status of a message in MongoDB.
     *
     * @param messageId The ID of the message to update.
     * @param consumerGroup The consumer group to which the message belongs.
     */
    private void updateMessageInMongo(String messageId, String consumerGroup) {
        Query query = new Query(Criteria.where(ID).is(messageId));
        Update update = new Update().set(CONSUMED, true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(false);

        Message updatedMessage = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        if (updatedMessage != null) {
            logger.debug("Message with ID: {} in Consumer Group: {} updated to consumed: {}", messageId, consumerGroup, true);
        } else {
            logger.warn("Message with ID: {} not found in Consumer Group: {} for update.", messageId, consumerGroup);
        }
    }
}
