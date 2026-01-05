package com.al.lightq.service;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service for reserving messages (pop), prioritizing cache and then falling
 * back to the database.
 * <p>
 * Operational Flow:
 * <ul>
 * <li><b>Cache First:</b> Checks Redis for candidates.</li>
 * <li><b>Self-Healing:</b> Validates candidates against DB. If missing or
 * consumed, removes from Redis (cleans junk).</li>
 * <li><b>Reservation:</b> Atomically reserves in DB (increments deliveryCount,
 * sets reservedUntil).</li>
 * <li><b>Removal:</b> On successful reservation from cache candidate, removes
 * from Redis using ZREM.</li>
 * <li><b>DLQ:</b> Moves messages exceeding maxDeliveryAttempts to DLQ via
 * DlqService.</li>
 * </ul>
 * </p>
 */
@Service
public class PopMessageService {

	private final MongoTemplate mongoTemplate;
	private final RedisQueueService redisQueueService;
	private final DlqService dlqService;
	private final LightQProperties lightQProperties;

	public PopMessageService(MongoTemplate mongoTemplate, RedisQueueService redisQueueService,
			LightQProperties lightQProperties, DlqService dlqService) {
		this.mongoTemplate = mongoTemplate;
		this.redisQueueService = redisQueueService;
		this.lightQProperties = lightQProperties;
		this.dlqService = dlqService;
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

		// Try reserving from cache candidates using a non-destructive peek, then
		// conditionally remove from cache after successful DB reservation.
		int scanWindow = Math.min(10, lightQProperties.getMessageAllowedToFetch());
		java.util.List<Message> candidates = redisQueueService.viewMessages(consumerGroup, scanWindow);
		for (Message candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			Optional<Message> reservedOpt = reserveById(candidate.getId(), consumerGroup);
			if (reservedOpt.isPresent()) {
				Message reserved = reservedOpt.get();
				if (reserved.getDeliveryCount() > maxAttempts) {
					logger.info("Message {} exceeded maxDeliveryAttempts ({}). Moving to DLQ for group {}",
							reserved.getId(), maxAttempts, consumerGroup);
					dlqService.moveToDlq(reserved, consumerGroup, DLQ_REASON_MAX_DELIVERIES);
					// do not remove the candidate from cache on DLQ move
					// (it will age out by TTL and subsequent cache views will skip it)
					continue;
				}
				// Reservation succeeded, remove exactly one occurrence from cache list
				redisQueueService.removeOne(consumerGroup, candidate);
				logger.debug("Reserved message {} from cache for group {} with deliveryCount {}", reserved.getId(),
						consumerGroup, reserved.getDeliveryCount());
				return Optional.of(reserved);
			}

			// SELF-HEALING: If reservation failed, check if it's because the message is
			// invalid/consumed.
			// If so, remove from Redis to clean up "junk".
			cleanupIfInvalid(candidate, consumerGroup);
		}

		// Fallback to DB-only reservation
		Optional<Message> dbReserved = reserveOldestAvailable(consumerGroup);
		while (dbReserved.isPresent()) {
			Message reserved = dbReserved.get();
			if (reserved.getDeliveryCount() > maxAttempts) {
				logger.info("Message {} exceeded maxDeliveryAttempts ({}). Moving to DLQ for group {}",
						reserved.getId(), maxAttempts, consumerGroup);
				dlqService.moveToDlq(reserved, consumerGroup, DLQ_REASON_MAX_DELIVERIES);
				dbReserved = reserveOldestAvailable(consumerGroup);
				continue;
			}
			logger.debug("Reserved message {} from DB for group {} with deliveryCount {}", reserved.getId(),
					consumerGroup, reserved.getDeliveryCount());
			return Optional.of(reserved);
		}

		logger.debug("No reservable message found for Consumer Group: {}", consumerGroup);
		return Optional.empty();
	}

	/**
	 * Checks if the message exists in DB and is in a valid state (not consumed). If
	 * not, removes it from Redis.
	 */
	private void cleanupIfInvalid(Message idxMessage, String consumerGroup) {
		try {
			Message dbMessage = mongoTemplate.findById(idxMessage.getId(), Message.class, consumerGroup);
			if (dbMessage == null) {
				logger.warn("Self-Healing: Message {} found in Redis but missing in DB. Removing from Redis.",
						idxMessage.getId());
				redisQueueService.removeOne(consumerGroup, idxMessage);
			} else if (dbMessage.isConsumed()) {
				logger.warn("Self-Healing: Message {} found in Redis but is consumed in DB. Removing from Redis.",
						idxMessage.getId());
				redisQueueService.removeOne(consumerGroup, idxMessage);
			}
			// If it exists and matches but couldn't be reserved, it's likely reserved by
			// another consumer
			// or scheduled for future. In that case, we leave it (it's valid).
		} catch (Exception e) {
			logger.warn("Self-Healing check failed for message {}", idxMessage.getId(), e);
		}
	}

	/**
	 * Attempts to reserve a single message by ID for the given consumer group.
	 * <p>
	 * Uses a findAndModify with conditional criteria to ensure the message is
	 * currently reservable (not consumed and either never reserved or reservation
	 * expired). On success:
	 * <ul>
	 * <li>Increments deliveryCount</li>
	 * <li>Sets reservedUntil to now + visibilityTimeoutSeconds</li>
	 * <li>Sets lastDeliveryAt to now</li>
	 * </ul>
	 * </p>
	 *
	 * @param messageId
	 *            message identifier to reserve
	 * @param consumerGroup
	 *            target consumer group (collection name)
	 * @return Optional containing the newly reserved Message, or empty if not
	 *         reservable
	 */
	private Optional<Message> reserveById(String messageId, String consumerGroup) {
		Date now = new Date();
		int vtSec = lightQProperties.getVisibilityTimeoutSeconds();
		Date until = new Date(now.getTime() + vtSec * 1000L);

		Criteria reservable = new Criteria().orOperator(Criteria.where(RESERVED_UNTIL).is(null),
				Criteria.where(RESERVED_UNTIL).lte(now));

		Criteria scheduleReady = new Criteria().orOperator(Criteria.where(SCHEDULED_AT).is(null),
				Criteria.where(SCHEDULED_AT).lte(now));

		Criteria combined = new Criteria().andOperator(reservable, scheduleReady);

		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false));
		query.addCriteria(combined);

		Update update = new Update().inc(DELIVERY_COUNT, 1).set(RESERVED_UNTIL, until).set(LAST_DELIVERY_AT, now);

		FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

		Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);
		return Optional.ofNullable(message);
	}

	/**
	 * Reserves the oldest available (FIFO by createdAt) message for the given
	 * consumer group.
	 * <p>
	 * Selects the oldest document where consumed=false and reservation is currently
	 * available (never reserved or reservation expired). On success, updates
	 * reservation fields similar to {@link #reserveById(String, String)}.
	 * </p>
	 *
	 * @param consumerGroup
	 *            target consumer group (collection name)
	 * @return Optional containing the reserved Message, or empty if none available
	 */
	private Optional<Message> reserveOldestAvailable(String consumerGroup) {
		Date now = new Date();
		int vtSec = lightQProperties.getVisibilityTimeoutSeconds();
		Date until = new Date(now.getTime() + vtSec * 1000L);

		Criteria reservable = new Criteria().orOperator(Criteria.where(RESERVED_UNTIL).is(null),
				Criteria.where(RESERVED_UNTIL).lte(now));

		Criteria scheduleReady = new Criteria().orOperator(Criteria.where(SCHEDULED_AT).is(null),
				Criteria.where(SCHEDULED_AT).lte(now));

		Criteria combined = new Criteria().andOperator(reservable, scheduleReady);

		Query query = new Query(Criteria.where(CONSUMED).is(false));
		query.addCriteria(combined);
		query.with(Sort.by(Sort.Direction.ASC, CREATED_AT));

		Update update = new Update().inc(DELIVERY_COUNT, 1).set(RESERVED_UNTIL, until).set(LAST_DELIVERY_AT, now);

		FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

		Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);
		return Optional.ofNullable(message);
	}
}
