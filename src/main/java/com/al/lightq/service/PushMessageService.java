package com.al.lightq.service;

import static com.al.lightq.LightQConstants.CONSUMED;
import static com.al.lightq.LightQConstants.CREATED_AT;
import static com.al.lightq.LightQConstants.RESERVED_UNTIL;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

/**
 * Service for pushing messages to the queue.
 * <p>
 * Handles message creation, validation, synchronous MongoDB persistence, and
 * Redis caching. Key responsibilities:
 * <ul>
 * <li>Validating input (consumerGroup, content).</li>
 * <li>Synchronous MongoDB persistence (ensures durability before cache).</li>
 * <li>Redis caching (ZSet addition) for immediate availability.</li>
 * <li>Scheduled delivery handling (persisted to DB, skipped from cache).</li>
 * <li>Batch processing with efficient pipelining and bulk inserts.</li>
 * </ul>
 * </p>
 */
@Service
public class PushMessageService {

	private static final Logger logger = LoggerFactory.getLogger(PushMessageService.class);
	private final MongoTemplate mongoTemplate;
	private final RedisQueueService redisQueueService;
	private final LightQProperties lightQProperties;
	// Tracks which consumer groups have had indexes ensured (bounded to avoid
	// memory growth)
	private final Cache<String, Boolean> indexCache;

	// For tests to override TTL via ReflectionTestUtils.setField("expireMinutes",
	// ...)
	private long expireMinutes;

	public PushMessageService(RedisQueueService redisQueueService, LightQProperties lightQProperties,
			MongoTemplate mongoTemplate) {
		this.redisQueueService = redisQueueService;
		this.lightQProperties = lightQProperties;
		this.mongoTemplate = mongoTemplate;
		this.indexCache = Caffeine.newBuilder().maximumSize(lightQProperties.getIndexCacheMaxGroups())
				.expireAfterAccess(java.time.Duration.ofMinutes(lightQProperties.getIndexCacheExpireMinutes())).build();
		// Initialize indexOps for reuse, or use it directly in ensureConsumerGroupIndex
		// But in this specific class logic, indexOps field was unused.
		// However, I will keep it if it was intended, or remove the field if truly
		// unused.
		// The lint said field indexOps is not used. I will remove the field
		// initialization.
	}

	/**
	 * Pushes a message to the queue.
	 * <p>
	 * The message is first added to a cache for immediate availability, and then
	 * saved to MongoDB asynchronously. A TTL index is ensured for the message's
	 * collection.
	 * </p>
	 *
	 * @param message
	 *            The {@link Message} object to be pushed.
	 * @return The {@link Message} that was pushed.
	 */
	public Message push(Message message) {
		int contentLength = message.getContent() != null ? message.getContent().length() : 0;
		logger.debug("Attempting to push message to Consumer Group: {} with contentLength={} chars",
				message.getConsumerGroup(), contentLength);

		// Persist to MongoDB FIRST (Synchronous) for durability
		persistToMongo(message);

		// If scheduled for future, skip cache
		if (message.getScheduledAt() != null && message.getScheduledAt().after(new Date())) {
			logger.debug("Message {} is scheduled for {}, skipping cache", message.getId(), message.getScheduledAt());
		} else {
			// Save the Message to Cache
			redisQueueService.addMessage(message);
			logger.debug("Message with ID {} added to cache for Consumer Group: {}", message.getId(),
					message.getConsumerGroup());
		}

		return message;
	}

	/**
	 * Asynchronously persists the message to MongoDB.
	 * <p>
	 * Ensures required indexes for the consumer group and inserts the document with
	 * bounded retries and exponential backoff. This is fire-and-forget and does not
	 * bounded retries and exponential backoff.
	 * </p>
	 *
	 * @param message
	 *            the message to persist
	 */
	private void persistToMongo(Message message) {
		createTTLIndex(message);
		boolean ok = insertWithRetry(message);
		if (ok) {
			logger.debug("Message with ID {} persisted in DB.", message.getId());
		} else {
			logger.error("Persist failed after retries for Message ID: {}", message.getId());
		}
	}

	/**
	 * Batch push messages. Adds all to cache using pipelined LPUSHALL per group and
	 * persists asynchronously to MongoDB grouped by consumerGroup.
	 *
	 * @param messages
	 *            messages to push; null/empty list is a no-op
	 * @return the input messages for convenience
	 */
	public List<Message> pushBatch(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return messages;
		}

		int size = messages.size();
		logger.debug("Attempting batch push of {} messages across groups", size);

		// Persist all to MongoDB FIRST (Synchronous)
		persistToMongoBatch(messages);

		// Filter messages eligible for cache (not scheduled in future)
		java.util.List<Message> immediateMessages = new java.util.ArrayList<>();
		Date now = new Date();
		for (Message m : messages) {
			if (m.getScheduledAt() == null || !m.getScheduledAt().after(now)) {
				immediateMessages.add(m);
			}
		}

		// Save to cache with one pipeline per call and single LPUSHALL per group
		if (!immediateMessages.isEmpty()) {
			redisQueueService.addMessages(immediateMessages);
		}

		return messages;
	}

	/**
	 * Persists a batch of messages to MongoDB grouped by consumer group.
	 * <p>
	 * Ensures indexes once per group and performs a bulk insert per group with
	 * bounded retries and exponential backoff.
	 * </p>
	 *
	 * @param messages
	 *            the messages to persist; null/empty list is ignored
	 */
	private void persistToMongoBatch(List<Message> messages) {
		try {
			// Group by consumer group
			final java.util.Map<String, java.util.List<Message>> byGroup = new java.util.HashMap<>();
			for (Message m : messages) {
				if (m == null || m.getConsumerGroup() == null) {
					continue;
				}
				byGroup.computeIfAbsent(m.getConsumerGroup(), k -> new java.util.ArrayList<>()).add(m);
			}
			// Ensure indexes once and bulk insert per group
			for (java.util.Map.Entry<String, java.util.List<Message>> e : byGroup.entrySet()) {
				java.util.List<Message> groupMsgs = e.getValue();
				if (groupMsgs.isEmpty()) {
					continue;
				}
				// Ensure indexes using the first message metadata
				createTTLIndex(groupMsgs.get(0));
				boolean ok = insertListWithRetry(groupMsgs, e.getKey());
				if (!ok) {
					logger.error("Async batch persist failed after retries for group {} ({} messages)", e.getKey(),
							groupMsgs.size());
				}
			}
			logger.debug("Batch persist attempted for {} messages asynchronously in DB across {} groups",
					messages.size(), byGroup.size());
		} catch (Exception e) {
			logger.error("Async batch persist failed for {} messages", messages == null ? 0 : messages.size(), e);
		}
	}

	/**
	 * Inserts a single message with bounded retry and exponential backoff.
	 *
	 * @param message
	 *            the message to insert
	 * @return true if insert eventually succeeded within retry budget; false
	 *         otherwise
	 */
	private boolean insertWithRetry(Message message) {
		final String collection = message.getConsumerGroup();
		final int maxAttempts = 3;
		long backoffMs = 100L;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				mongoTemplate.insert(message, collection);
				return true;
			} catch (Exception e) {
				logger.warn("Insert attempt {} failed for id={} group={}: {}", attempt, message.getId(), collection,
						e.toString());
				if (attempt == maxAttempts) {
					return false;
				}
				try {
					Thread.sleep(backoffMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				backoffMs = Math.min(1000L, backoffMs * 3);
			}
		}
		return false;
	}

	/**
	 * Inserts a list of messages into the given collection with bounded retry and
	 * exponential backoff.
	 *
	 * @param groupMsgs
	 *            the messages to insert
	 * @param collection
	 *            the MongoDB collection (consumer group) name
	 * @return true if insert eventually succeeded within retry budget; false
	 *         otherwise
	 */
	private boolean insertListWithRetry(java.util.List<Message> groupMsgs, String collection) {
		final int maxAttempts = 3;
		long backoffMs = 100L;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				mongoTemplate.insert(groupMsgs, collection);
				return true;
			} catch (Exception e) {
				logger.warn("Batch insert attempt {} failed for group {} size {}: {}", attempt, collection,
						groupMsgs.size(), e.toString());
				if (attempt == maxAttempts) {
					return false;
				}
				try {
					Thread.sleep(backoffMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
				backoffMs = Math.min(1000L, backoffMs * 3);
			}
		}
		return false;
	}

	/**
	 * Ensures the necessary indexes for the consumer group's collection:
	 * <ul>
	 * <li>Partial TTL index on createdAt for documents where consumed=true</li>
	 * <li>Compound index on { consumed, reservedUntil, createdAt } to speed
	 * reads</li>
	 * </ul>
	 * TTL minutes are sourced from LightQProperties, or can be overridden in tests
	 * via expireMinutes.
	 *
	 * @param message
	 *            The {@link Message} providing the target consumer group
	 *            (collection)
	 */
	private void createTTLIndex(Message message) {
		String collection = message.getConsumerGroup();
		indexCache.get(collection, k -> {
			long minutes = (this.expireMinutes > 0)
					? this.expireMinutes
					: lightQProperties.getPersistenceDurationMinutes();
			logger.debug("Ensuring indexes for collection: {} (TTL on {}, compound on {},{}, {})", collection,
					CREATED_AT, CONSUMED, RESERVED_UNTIL, CREATED_AT);
			// TTL index on createdAt for consumed=true only (partial index)
			mongoTemplate.indexOps(collection)
					.ensureIndex(new Index().on(CREATED_AT, Sort.Direction.ASC).expire(minutes, TimeUnit.MINUTES)
							.partial(org.springframework.data.mongodb.core.index.PartialIndexFilter.of(
									org.springframework.data.mongodb.core.query.Criteria.where(CONSUMED).is(true))));
			// Compound index to speed up read path: { consumed: 1, reservedUntil: 1,
			// createdAt: 1 }
			mongoTemplate.indexOps(collection).ensureIndex(new Index().on(CONSUMED, Sort.Direction.ASC)
					.on(RESERVED_UNTIL, Sort.Direction.ASC).on(CREATED_AT, Sort.Direction.ASC));
			logger.debug("Indexes ensured for collection: {}", collection);
			return Boolean.TRUE;
		});
	}
}
