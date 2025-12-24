package com.al.lightq.service;

import static com.al.lightq.LightQConstants.CONSUMED;
import static com.al.lightq.LightQConstants.ID;
import static com.al.lightq.LightQConstants.LAST_ERROR;
import static com.al.lightq.LightQConstants.RESERVED_UNTIL;

import com.al.lightq.model.Message;
import com.mongodb.client.result.UpdateResult;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Service handling ack/nack and visibility extension for reserved messages.
 * Correctness is anchored in MongoDB using atomic operations.
 */
@Service
public class AcknowledgementService {

	private static final Logger logger = LoggerFactory.getLogger(AcknowledgementService.class);

	private final MongoTemplate mongoTemplate;

	public AcknowledgementService(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Acknowledge a message: marks consumed=true and clears reservation.
	 * Idempotent: returns true if already consumed or updated.
	 */
	public boolean ack(String consumerGroup, String messageId) {
		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false));
		Update update = new Update().set(CONSUMED, true).set(RESERVED_UNTIL, null);

		UpdateResult result = mongoTemplate.updateFirst(query, update, Message.class, consumerGroup);
		if (result.getModifiedCount() > 0) {
			logger.info("Ack succeeded for messageId={} in group={}", messageId, consumerGroup);
			return true;
		}

		// Idempotency: consider already consumed as success
		Query already = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(true));
		boolean exists = mongoTemplate.exists(already, Message.class, consumerGroup);
		if (exists) {
			logger.info("Ack idempotent success (already consumed) for messageId={} in group={}", messageId,
					consumerGroup);
			return true;
		}

		// Not found; return not found (404)
		logger.info("Ack not found for messageId={} in group={}", messageId, consumerGroup);
		return false;
	}

	/**
	 * Negative acknowledgement: immediately re-queues the message by setting
	 * reservedUntil to now. No-op if the message is not currently
	 * reserved/unconsumed.
	 */
	public boolean nack(String consumerGroup, String messageId, String reason) {
		Date now = new Date();
		// Only allow nack if message is not consumed and is/was reserved (reservedUntil
		// != null)
		Criteria reservable = new Criteria().orOperator(Criteria.where(RESERVED_UNTIL).is(null),
				Criteria.where(RESERVED_UNTIL).gte(new Date(0)) // accept any present value
		);

		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false)).addCriteria(reservable);

		Update update = new Update().set(RESERVED_UNTIL, now).set(LAST_ERROR, reason);

		UpdateResult result = mongoTemplate.updateFirst(query, update, Message.class, consumerGroup);
		boolean modified = result.getModifiedCount() > 0;
		if (modified) {
			logger.info("Nack succeeded for messageId={} in group={} (reason={})", messageId, consumerGroup, reason);
		} else {
			logger.info("Nack no-op for messageId={} in group={} (not found or already consumed)", messageId,
					consumerGroup);
		}
		return modified;
	}

	/**
	 * Extends visibility timeout for a reserved message. Sets reservedUntil to now
	 * + extensionSeconds if message is unconsumed and was reserved.
	 */
	public boolean extendVisibility(String consumerGroup, String messageId, int extensionSeconds) {
		Date now = new Date();
		Date newUntil = new Date(now.getTime() + Math.max(1, extensionSeconds) * 1000L);

		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false).and(RESERVED_UNTIL).gt(now));

		Update update = new Update().set(RESERVED_UNTIL, newUntil);

		FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
		Message updated = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);
		boolean success = updated != null;
		if (success) {
			logger.info("Extended visibility for messageId={} in group={} until={}", messageId, consumerGroup,
					newUntil);
		} else {
			logger.info("Extend-visibility no-op for messageId={} in group={} (not reserved or not found)", messageId,
					consumerGroup);
		}
		return success;
	}

	/**
	 * Helper to check presence (for testing or future APIs).
	 */
	public Optional<Message> findById(String consumerGroup, String messageId) {
		return Optional.ofNullable(mongoTemplate.findById(messageId, Message.class, consumerGroup));
	}
}
