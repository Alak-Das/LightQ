package com.al.lightq.service;

import com.al.lightq.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Service to promote scheduled messages from MongoDB to Redis when they become
 * due.
 */
@Service
public class ScheduledMessagePromoter {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledMessagePromoter.class);

    private final MongoTemplate mongoTemplate;
    private final RedisQueueService redisQueueService;

    public ScheduledMessagePromoter(MongoTemplate mongoTemplate, RedisQueueService redisQueueService) {
        this.mongoTemplate = mongoTemplate;
        this.redisQueueService = redisQueueService;
    }

    @Scheduled(fixedRateString = "${lightq.scheduled-promoter.rate-ms:5000}")
    public void promoteScheduledMessages() {
        Date now = new Date();
        int promotedCount = 0;
        // Limit per run to avoid holding thread too long if backlog is huge
        int maxPromotionsPerRun = 100;

        for (int i = 0; i < maxPromotionsPerRun; i++) {
            // Atomic find-and-modify:
            // 1. Find a message where scheduledAt <= now AND consumed is false
            // 2. Unset scheduledAt (effectively making it a "normal" message)
            // 3. Return the OLD document so we can read the scheduledAt time for scoring
            Message msg = mongoTemplate.findAndModify(
                    Query.query(Criteria.where("scheduledAt").lte(now).and("consumed").is(false)),
                    new Update().unset("scheduledAt"),
                    FindAndModifyOptions.options().returnNew(false),
                    Message.class);

            if (msg == null) {
                break; // No more due messages found
            }

            if (msg.getScheduledAt() != null) {
                // Push to Redis using the original scheduled time as priority score.
                // This ensures that if it was due 5 minutes ago, it gets a lower score
                // (higher priority) than a message created just now.
                redisQueueService.addMessage(msg, (double) msg.getScheduledAt().getTime());
                promotedCount++;
            }
        }

        if (promotedCount > 0) {
            logger.info("Promoted {} scheduled messages to pending queue", promotedCount);
        }
    }
}
