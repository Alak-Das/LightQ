package com.al.lightq.service;

import com.al.lightq.model.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.al.lightq.util.LightQConstants.*;

/**
 * Service for viewing messages in the queue for a specific consumer group.
 * It retrieves messages from both cache and MongoDB, combines them, and applies filtering and sorting.
 */
@Service
public class ViewMessageService {

    private static final Logger logger = LoggerFactory.getLogger(ViewMessageService.class);
    private final MongoTemplate mongoTemplate;
    private final CacheService cacheService;

    public ViewMessageService(MongoTemplate mongoTemplate, CacheService cacheService) {
        this.mongoTemplate = mongoTemplate;
        this.cacheService = cacheService;
    }

    /**
     * Retrieves a list of messages for a given consumer group, with options to limit the count and filter by consumption status.
     * Messages are first retrieved from the cache and then from MongoDB, excluding duplicates.
     *
     * @param consumerGroup The consumer group for which to retrieve messages.
     * @param messageCount  The maximum number of messages to return.
     * @param consumed      An optional string ("yes" or "no") to filter messages by their consumed status.
     * @return A sorted list of unique messages.
     */
    public List<Message> view(String consumerGroup, int messageCount, String consumed) {
        logger.debug("Received request to view messages for Consumer Group: {}, message count: {}, consumed status: {}", consumerGroup, messageCount, StringUtils.isEmpty(consumed) ? "N/A" : consumed);

        List<Message> combinedMessages = new ArrayList<>();

        Query query = new Query();

        if (consumed != null) {
            if (consumed.equalsIgnoreCase("yes")) {
                query.addCriteria(Criteria.where(CONSUMED).is(true));
                logger.debug("Filtering messages to include only consumed messages.");
            } else {
                // Get from Cache
                List<Message> cachedMessages = cacheService.viewMessages(consumerGroup).stream().limit(messageCount).toList();
                combinedMessages.addAll(cachedMessages);
                logger.debug("Retrieved {} messages from cache for Consumer Group: {}", cachedMessages.size(), consumerGroup);

                Set<String> cachedMessageIds = cachedMessages.stream()
                        .map(Message::getId)
                        .collect(Collectors.toSet());

                if (!cachedMessageIds.isEmpty()) {
                    if (cachedMessageIds.size() < messageCount) {
                        query.addCriteria(Criteria.where(ID).nin(cachedMessageIds));
                        logger.debug("Excluding {} cached messages from MongoDB query.", cachedMessageIds.size());
                    } else {
                        logger.debug("All requested messages found in cache. Skipping MongoDB query.");
                        // Sort by createdAt to maintain consistent order
                        combinedMessages.sort(Comparator.comparing(Message::getCreatedAt));
                        logger.debug("Returning a combined list of {} unique messages for Consumer Group: {}", combinedMessages.size(), consumerGroup);
                        return combinedMessages;
                    }
                }
                query.addCriteria(Criteria.where(CONSUMED).is(false));
                logger.debug("Filtering messages to include only unconsumed messages (after checking cache).");
            }
        }

        query.limit(Math.max(0, messageCount - combinedMessages.size()));
        List<Message> mongoMessages = mongoTemplate.find(query, Message.class, consumerGroup);
        combinedMessages.addAll(mongoMessages);
        logger.debug("Retrieved {} messages from MongoDB for Consumer Group: {}.", mongoMessages.size(), consumerGroup);

        // Sort by createdAt to maintain consistent order
        combinedMessages.sort(Comparator.comparing(Message::getCreatedAt));

        logger.info("Returning a combined list of {} unique messages for Consumer Group: {}", combinedMessages.size(), consumerGroup);
        return combinedMessages;
    }
}
