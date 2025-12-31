package com.al.lightq.service;

import static com.al.lightq.LightQConstants.CONSUMED;
import static com.al.lightq.LightQConstants.CREATED_AT;
import static com.al.lightq.LightQConstants.RESERVED_UNTIL;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for pushing messages to the queue.
 * <p>
 * Messages are added to a cache and then asynchronously persisted to MongoDB
 * with a TTL index.
 * </p>
 */
@Service
public class PushMessageService {

	private static final Logger logger = LoggerFactory.getLogger(PushMessageService.class);
	private final MongoTemplate mongoTemplate;
	private final CacheService cacheService;
	private final LightQProperties lightQProperties;
	// Tracks which consumer groups have had indexes ensured (bounded to avoid
	// memory growth)
	private final Cache<String, Boolean> indexCache;
	// For tests to override TTL via ReflectionTestUtils.setField("expireMinutes",
	// ...)
	private long expireMinutes;

	public PushMessageService(MongoTemplate mongoTemplate, CacheService cacheService,
			LightQProperties lightQProperties) {
		this.mongoTemplate = mongoTemplate;
		this.cacheService = cacheService;
		this.lightQProperties = lightQProperties;
		this.indexCache = Caffeine.newBuilder().maximumSize(lightQProperties.getIndexCacheMaxGroups())
				.expireAfterAccess(java.time.Duration.ofMinutes(lightQProperties.getIndexCacheExpireMinutes())).build();
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
		// Save the Message to Cache
		cacheService.addMessage(message);
		logger.debug("Message with ID {} added to cache for Consumer Group: {}", message.getId(),
				message.getConsumerGroup());

		// Fire-and-forget MongoDB persistence
		persistToMongo(message);

		return message;
	}

	@Async("taskExecutor")
	public void persistToMongo(Message message) {
		createTTLIndex(message);
		boolean ok = insertWithRetry(message);
		if (ok) {
			logger.debug("Message with ID {} persisted asynchronously in DB.", message.getId());
		} else {
			logger.error("Async persist failed after retries for Message ID: {}", message.getId());
		}
	}

	/**
	 * Ensures a TTL (Time-To-Live) index exists on the 'createdAt' field for the
	 * message's collection.
	 * <p>
	 * This index automatically deletes documents after a specified time.
	 * </p>
	 *
	 * @param message
	 *            The {@link Message} for which to ensure the TTL index.
	 */
	/**
	 * Batch push messages. Adds all to cache using pipelined LPUSHALL per group and
	 * persists asynchronously to MongoDB grouped by consumerGroup.
	 *
	 * @param messages
	 *            messages to push; null/empty list is a no-op
	 * @return the input messages for convenience
	 */
	public java.util.List<Message> pushBatch(java.util.List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		int size = messages.size();
		logger.debug("Attempting batch push of {} messages across groups", size);

		// Save to cache with one pipeline per call and single LPUSHALL per group
		cacheService.addMessages(messages);

		// Fire-and-forget MongoDB persistence in batch
		persistToMongoBatch(messages);

		return messages;
	}

	@Async("taskExecutor")
	public void persistToMongoBatch(java.util.List<Message> messages) {
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
