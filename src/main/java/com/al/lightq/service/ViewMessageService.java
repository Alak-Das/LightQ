package com.al.lightq.service;

import com.al.lightq.config.LightQProperties;
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
    private final LightQProperties lightQProperties;

    public ViewMessageService(MongoTemplate mongoTemplate, CacheService cacheService, LightQProperties lightQProperties) {
        this.mongoTemplate = mongoTemplate;
        this.cacheService = cacheService;
        this.lightQProperties = lightQProperties;
    }

    /**
     * Retrieves a list of messages for a given consumer group, with options to limit the count and filter by consumption status.
     * Messages are first retrieved from the cache and then from MongoDB, excluding duplicates.
     *
     * @param consumerGroup The consumer group for which to retrieve messages.
     * @param consumed      An optional string ("yes" or "no") to filter messages by their consumed status.
     * @return A sorted list of unique messages.
     */
    public List<Message> view(String consumerGroup, String consumed) {
        final int limit = lightQProperties.getMessageAllowedToFetch();
        final boolean hasConsumedParam = StringUtils.isNotBlank(consumed);
        final Boolean consumedFlag = hasConsumedParam ? Boolean.valueOf("yes".equalsIgnoreCase(consumed)) : null;
        logger.debug("View request: consumerGroup={}, messageCount={}, consumed={}", consumerGroup, limit, hasConsumedParam ? consumed : "N/A");

        // Fast-path: only consumed => DB only
        if (Boolean.TRUE.equals(consumedFlag)) {
            Query query = new Query()
                    .addCriteria(Criteria.where(CONSUMED).is(true))
                    .limit(limit);
            List<Message> fromDb = mongoTemplate.find(query, Message.class, consumerGroup);
            fromDb.sort(Comparator.comparing(Message::getCreatedAt));
            logger.info("Returning {} consumed messages from DB for Consumer Group: {}", fromDb.size(), consumerGroup);
            return fromDb;
        }

        // Cache-first for unconsumed or no filter
        List<Message> cached = cacheService.viewMessages(consumerGroup).stream().limit(limit).toList();
        logger.debug("Cache returned {} {} messages for consumerGroup={}",
                cached.size(),
                consumedFlag == null ? "(no consumed filter)" : "unconsumed",
                consumerGroup);

        if (cached.size() >= limit) {
            List<Message> result = new ArrayList<>(cached);
            result.sort(Comparator.comparing(Message::getCreatedAt));
            logger.info("Returning {} {} messages for Consumer Group: {}",
                    result.size(),
                    consumedFlag == null ? "(no consumed filter)" : "unconsumed",
                    consumerGroup);
            return result;
        }

        Set<String> cachedIds = cached.stream().map(Message::getId).collect(Collectors.toSet());
        int remaining = limit - cached.size();

        Query query = new Query().limit(remaining);
        if (Boolean.FALSE.equals(consumedFlag)) {
            query.addCriteria(Criteria.where(CONSUMED).is(false));
        }
        if (!cachedIds.isEmpty()) {
            query.addCriteria(Criteria.where(ID).nin(cachedIds));
            logger.debug("Excluding {} cached IDs from MongoDB query{}", cachedIds.size(),
                    consumedFlag == null ? " (no consumed filter)" : "");
        }

        List<Message> fromDb = mongoTemplate.find(query, Message.class, consumerGroup);

        List<Message> combined = new ArrayList<>(cached.size() + fromDb.size());
        combined.addAll(cached);
        combined.addAll(fromDb);
        combined.sort(Comparator.comparing(Message::getCreatedAt));

        if (consumedFlag == null) {
            logger.debug("MongoDB returned {} additional messages (no consumed filter) for consumerGroup={}", fromDb.size(), consumerGroup);
            logger.info("Returning {} messages (no consumed filter) for Consumer Group: {}", combined.size(), consumerGroup);
        } else {
            logger.debug("MongoDB returned {} additional unconsumed messages for consumerGroup={}", fromDb.size(), consumerGroup);
            logger.info("Returning {} unconsumed messages for Consumer Group: {}", combined.size(), consumerGroup);
        }
        return combined;
    }
}
