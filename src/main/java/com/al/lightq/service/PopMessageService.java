package com.al.lightq.service;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service for reserving messages (pop), prioritizing cache and then falling back to the
 * database.
 * <p>
 * For cache candidates, attempts reservation by ID; otherwise reserves the oldest available
 * message from MongoDB. Reservation increments deliveryCount and sets reservedUntil to
 * now + visibilityTimeoutSeconds. A message is NOT marked consumed until explicitly acked.
 * Messages exceeding maxDeliveryAttempts are moved to the DLQ with reason "max-deliveries".
 * </p>
 */
@Service
public class PopMessageService {

	private final MongoTemplate mongoTemplate;
	private final CacheService cacheService;
	private final LightQProperties lightQProperties;

	public PopMessageService(MongoTemplate mongoTemplate, CacheService cacheService,
			LightQProperties lightQProperties) {
		this.mongoTemplate = mongoTemplate;
		this.cacheService = cacheService;
		this.lightQProperties = lightQProperties;
	}

	private static final Logger logger = LoggerFactory.getLogger(PopMessageService.class);

	/**
	 * Pops the oldest available message for a given consumer group.
	 * <p>
	 * It first tries to fetch the message from the cache. If not found, it fetches
	 * from the database. Messages fetched from cache are asynchronously marked as
	 * consumed in the database.
	 * </p>
	 *
	 * @param consumerGroup
	 *            The consumer group from which to pop the message.
	 * @return An {@link Optional} containing the message if found, or empty if no
	 *         message is available.
	 */
	public Optional<Message> pop(String consumerGroup) {
		logger.debug("Attempting to reserve oldest message for Consumer Group: {}", consumerGroup);

		int maxAttempts = lightQProperties.getMaxDeliveryAttempts();

		// Try reserving from cache candidates
		Message cached = cacheService.popMessage(consumerGroup);
		while (cached != null) {
			Optional<Message> reservedOpt = reserveById(cached.getId(), consumerGroup);
			if (reservedOpt.isPresent()) {
				Message reserved = reservedOpt.get();
				if (reserved.getDeliveryCount() > maxAttempts) {
					logger.info("Message {} exceeded maxDeliveryAttempts ({}). Moving to DLQ for group {}",
							reserved.getId(), maxAttempts, consumerGroup);
					moveToDlq(reserved, consumerGroup, DLQ_REASON_MAX_DELIVERIES);
					cached = cacheService.popMessage(consumerGroup);
					continue;
				}
				logger.info("Reserved message {} from cache for group {} with deliveryCount {}", reserved.getId(),
						consumerGroup, reserved.getDeliveryCount());
				return Optional.of(reserved);
			}
			// not reservable (stale/missing/reserved), drop and try next
			cached = cacheService.popMessage(consumerGroup);
		}

		// Fallback to DB-only reservation
		Optional<Message> dbReserved = reserveOldestAvailable(consumerGroup);
		while (dbReserved.isPresent()) {
			Message reserved = dbReserved.get();
			if (reserved.getDeliveryCount() > maxAttempts) {
				logger.info("Message {} exceeded maxDeliveryAttempts ({}). Moving to DLQ for group {}",
						reserved.getId(), maxAttempts, consumerGroup);
				moveToDlq(reserved, consumerGroup, DLQ_REASON_MAX_DELIVERIES);
				dbReserved = reserveOldestAvailable(consumerGroup);
				continue;
			}
			logger.info("Reserved message {} from DB for group {} with deliveryCount {}", reserved.getId(),
					consumerGroup, reserved.getDeliveryCount());
			return Optional.of(reserved);
		}

		logger.debug("No reservable message found for Consumer Group: {}", consumerGroup);
		return Optional.empty();
	}

	private Optional<Message> reserveById(String messageId, String consumerGroup) {
		Date now = new Date();
		int vtSec = lightQProperties.getVisibilityTimeoutSeconds();
		Date until = new Date(now.getTime() + vtSec * 1000L);

		Criteria reservable = new Criteria().orOperator(Criteria.where(RESERVED_UNTIL).is(null),
				Criteria.where(RESERVED_UNTIL).lte(now));

		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false)).addCriteria(reservable);

		Update update = new Update().inc(DELIVERY_COUNT, 1).set(RESERVED_UNTIL, until).set(LAST_DELIVERY_AT, now);

		FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

		Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);
		return Optional.ofNullable(message);
	}

	private Optional<Message> reserveOldestAvailable(String consumerGroup) {
		Date now = new Date();
		int vtSec = lightQProperties.getVisibilityTimeoutSeconds();
		Date until = new Date(now.getTime() + vtSec * 1000L);

		Criteria reservable = new Criteria().orOperator(Criteria.where(RESERVED_UNTIL).is(null),
				Criteria.where(RESERVED_UNTIL).lte(now));

		Query query = new Query(Criteria.where(CONSUMED).is(false)).addCriteria(reservable)
				.with(Sort.by(Sort.Direction.ASC, CREATED_AT));

		Update update = new Update().inc(DELIVERY_COUNT, 1).set(RESERVED_UNTIL, until).set(LAST_DELIVERY_AT, now);

		FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

		Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);
		return Optional.ofNullable(message);
	}

	private void moveToDlq(Message message, String consumerGroup, String reason) {
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
		logger.info("DLQ move completed: id={}, group={}, reason={}", message.getId(), consumerGroup, reason);
	}

	private void ensureDlqIndexes(String dlqCollection) {
		Integer ttl = lightQProperties.getDlqTtlMinutes();
		if (ttl != null && ttl > 0) {
			logger.debug("Ensuring DLQ TTL index: collection={}, ttlMinutes={}", dlqCollection, ttl);
			mongoTemplate.indexOps(dlqCollection)
					.ensureIndex(new Index().on(CREATED_AT, Sort.Direction.ASC).expire(ttl, TimeUnit.MINUTES));
			logger.debug("DLQ TTL index ensured: collection={}", dlqCollection);
		} else {
			logger.debug("DLQ TTL not configured or disabled; collection={}", dlqCollection);
		}
	}
}
