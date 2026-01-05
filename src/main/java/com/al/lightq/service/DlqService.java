package com.al.lightq.service;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Dead-Letter Queue (DLQ) operations.
 * <p>
 * Central service for handling message failures and DLQ management. Key
 * responsibilities:
 * <ul>
 * <li><b>Move to DLQ:</b> Atomically marks message as consumed in main queue
 * and copies to DLQ collection.</li>
 * <li><b>Inspect:</b> View recent DLQ entries including failure reasons.</li>
 * <li><b>Replay:</b> Restore messages from DLQ to main queue (new ID, fresh
 * state) for redelivery.</li>
 * <li><b>Maintenance:</b> Ensures TTL indexes for DLQ collections.</li>
 * </ul>
 * </p>
 */
@Service
public class DlqService {

	private static final Logger logger = LoggerFactory.getLogger(DlqService.class);

	private final MongoTemplate mongoTemplate;
	private final RedisQueueService redisQueueService;
	private final LightQProperties lightQProperties;
	private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

	public DlqService(MongoTemplate mongoTemplate, RedisQueueService redisQueueService,
			LightQProperties lightQProperties, io.micrometer.core.instrument.MeterRegistry meterRegistry) {
		this.mongoTemplate = mongoTemplate;
		this.redisQueueService = redisQueueService;
		this.lightQProperties = lightQProperties;
		this.meterRegistry = meterRegistry;
	}

	/**
	 * Returns the most recent DLQ entries for the given consumer group.
	 * <p>
	 * Results are ordered by failedAt descending (newest first) and limited to the
	 * provided size.
	 * </p>
	 *
	 * @param consumerGroup
	 *            the target consumer group whose DLQ to inspect
	 * @param limit
	 *            maximum number of DLQ documents to return
	 * @return list of DLQ documents; empty list if none found
	 */
	public List<Document> view(String consumerGroup, int limit) {
		String dlqCollection = consumerGroup + lightQProperties.getDlqSuffix();
		Query q = new Query().with(Sort.by(Sort.Direction.DESC, FAILED_AT)).limit(limit);
		List<Document> docs = mongoTemplate.find(q, Document.class, dlqCollection);
		int size = docs != null ? docs.size() : 0;
		logger.debug("DLQ view: group={}, limit={}, returned={}", consumerGroup, limit, size);
		return docs != null ? docs : List.of();
	}

	/**
	 * Replay DLQ entries by document IDs.
	 * <p>
	 * For each provided DLQ document id:
	 * <ul>
	 * <li>Create a new Message with a fresh id and same content/consumerGroup</li>
	 * <li>Save to the main collection (consumed=false)</li>
	 * <li>Push to cache for immediate availability</li>
	 * <li>Delete the original DLQ document</li>
	 * </ul>
	 * </p>
	 *
	 * @param consumerGroup
	 *            the target consumer group whose DLQ entries should be replayed
	 * @param ids
	 *            list of DLQ document ids to replay; null/empty is treated as no-op
	 * @return number of entries successfully replayed
	 */
	public int replay(String consumerGroup, List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			logger.debug("DLQ replay requested with empty ids for group={}", consumerGroup);
			return 0;
		}
		String dlqCollection = consumerGroup + lightQProperties.getDlqSuffix();
		int count = 0;
		for (String id : ids) {
			Document doc = mongoTemplate.findById(id, Document.class, dlqCollection);
			if (doc == null) {
				logger.debug("DLQ replay: id={} not found in {}", id, dlqCollection);
				continue;
			}
			String content = doc.getString(CONTENT);
			if (content == null) {
				logger.warn("DLQ replay: id={} missing content, skipping", id);
				continue;
			}
			// Create new message with a fresh id
			String newId = UUID.randomUUID().toString();
			Message message = new Message(newId, consumerGroup, content);
			// Ensure fields for correctness
			// createdAt initialized in Message(messageId, consumerGroup, content) ctor
			// consumed defaults false

			// Save to main collection then push to cache
			mongoTemplate.save(message, consumerGroup);
			redisQueueService.addMessage(message);

			// Remove DLQ entry
			mongoTemplate.remove(new Query(Criteria.where(ID).is(id)), dlqCollection);

			count++;
			logger.info("DLQ replayed id={} into new messageId={} for group={}", id, newId, consumerGroup);
		}
		logger.info("DLQ replay completed: group={}, requested={}, replayed={}", consumerGroup, ids.size(), count);
		return count;
	}

	/**
	 * Moves the given message to the Dead Letter Queue (DLQ) for the consumer
	 * group.
	 * <p>
	 * Actions performed:
	 * <ul>
	 * <li>Ensures DLQ indexes if TTL is configured</li>
	 * <li>Inserts a copy of the message into the DLQ collection with failure
	 * metadata</li>
	 * <li>Marks the original message as consumed to exclude it from future
	 * reservation</li>
	 * </ul>
	 * </p>
	 *
	 * @param message
	 *            the message to move to DLQ
	 * @param consumerGroup
	 *            source consumer group
	 * @param reason
	 *            reason for DLQ (e.g., "max-deliveries")
	 */
	public void moveToDlq(Message message, String consumerGroup, String reason) {
		String dlqCollection = consumerGroup + lightQProperties.getDlqSuffix();

		// ensure DLQ TTL index if configured
		ensureDlqIndexes(dlqCollection);

		// Insert copy into DLQ with failure metadata
		Document doc = new Document();
		doc.put(ID, message.getId());
		doc.put(CONTENT, message.getContent());
		doc.put(CONSUMER_GROUP, message.getConsumerGroup());
		doc.put(CREATED_AT, message.getCreatedAt());
		doc.put(CONSUMED, true);
		doc.put(DELIVERY_COUNT, message.getDeliveryCount());
		doc.put(LAST_DELIVERY_AT, message.getLastDeliveryAt());
		doc.put(LAST_ERROR, message.getLastError());
		doc.put(FAILED_AT, new Date());
		doc.put(DLQ_REASON, reason);

		mongoTemplate.insert(doc, dlqCollection);
		logger.debug("DLQ insert: id={}, collection={}, reason={}", message.getId(), dlqCollection, reason);

		// Mark original as consumed to exclude from future reservation
		Query q = new Query(Criteria.where(ID).is(message.getId()));
		Update u = new Update().set(CONSUMED, true).set(RESERVED_UNTIL, null);
		mongoTemplate.updateFirst(q, u, Message.class, consumerGroup);

		meterRegistry.counter("lightq.messages.dlq.total", "consumerGroup", consumerGroup, "reason", reason)
				.increment();

		logger.info("DLQ move completed: id={}, group={}, reason={}", message.getId(), consumerGroup, reason);
	}

	/**
	 * Ensures TTL index for the DLQ collection if a positive TTL is configured.
	 * <p>
	 * When enabled, documents in the DLQ will expire automatically after the
	 * configured number of minutes. If TTL is null or non-positive, no TTL index is
	 * created.
	 * </p>
	 *
	 * @param dlqCollection
	 *            the DLQ collection name (group + suffix)
	 */
	private void ensureDlqIndexes(String dlqCollection) {
		Integer ttl = lightQProperties.getDlqTtlMinutes();
		if (ttl != null && ttl > 0) {
			logger.debug("Ensuring DLQ TTL index: collection={}, ttlMinutes={}", dlqCollection, ttl);
			mongoTemplate.indexOps(dlqCollection)
					.createIndex(new Index().on(CREATED_AT, Sort.Direction.ASC).expire(ttl, TimeUnit.MINUTES));
			logger.debug("DLQ TTL index ensured: collection={}", dlqCollection);
		} else {
			logger.debug("DLQ TTL not configured or disabled; collection={}", dlqCollection);
		}
	}
}
