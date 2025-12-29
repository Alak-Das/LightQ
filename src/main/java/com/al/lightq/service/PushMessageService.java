package com.al.lightq.service;

import static com.al.lightq.LightQConstants.CONSUMED;
import static com.al.lightq.LightQConstants.CREATED_AT;
import static com.al.lightq.LightQConstants.RESERVED_UNTIL;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
	// Tracks which consumer groups have had indexes ensured to avoid repeated work
	private final ConcurrentMap<String, Boolean> indexesEnsured = new ConcurrentHashMap<>();
	// For tests to override TTL via ReflectionTestUtils.setField("expireMinutes",
	// ...)
	private long expireMinutes;

	public PushMessageService(MongoTemplate mongoTemplate, CacheService cacheService,
			LightQProperties lightQProperties) {
		this.mongoTemplate = mongoTemplate;
		this.cacheService = cacheService;
		this.lightQProperties = lightQProperties;
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
		try {
			createTTLIndex(message);
			mongoTemplate.insert(message, message.getConsumerGroup());
			logger.info("Message with ID {} persisted asynchronously in DB.", message.getId());
		} catch (Exception e) {
			logger.error("Async persist failed for Message ID: {}", message.getId(), e);
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
	private void createTTLIndex(Message message) {
		String collection = message.getConsumerGroup();
		if (Boolean.TRUE.equals(indexesEnsured.get(collection))) {
			return;
		}
		synchronized (indexesEnsured) {
			if (Boolean.TRUE.equals(indexesEnsured.get(collection))) {
				return;
			}
			long minutes = (this.expireMinutes > 0)
					? this.expireMinutes
					: lightQProperties.getPersistenceDurationMinutes();
			logger.debug("Ensuring indexes for collection: {} (TTL on {}, compound on {},{}, {})", collection,
					CREATED_AT, CONSUMED, RESERVED_UNTIL, CREATED_AT);
			// TTL index on createdAt
			mongoTemplate.indexOps(collection)
					.ensureIndex(new Index().on(CREATED_AT, Sort.Direction.ASC).expire(minutes, TimeUnit.MINUTES));
			// Compound index to speed up read path: { consumed: 1, reservedUntil: 1,
			// createdAt: 1 }
			mongoTemplate.indexOps(collection).ensureIndex(new Index().on(CONSUMED, Sort.Direction.ASC)
					.on(RESERVED_UNTIL, Sort.Direction.ASC).on(CREATED_AT, Sort.Direction.ASC));
			indexesEnsured.put(collection, true);
			logger.debug("Indexes ensured for collection: {}", collection);
		}
	}
}
