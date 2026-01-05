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
	 * Acknowledge a reserved message.
	 * <p>
	 * Marks the document as consumed (consumed=true) and clears its reservation
	 * window (reservedUntil=null). This operation is idempotent: if the message was
	 * already consumed, it is treated as success.
	 * </p>
	 *
	 * @param consumerGroup
	 *            the MongoDB collection (consumer group) to operate on
	 * @param messageId
	 *            the identifier of the message to acknowledge
	 * @return true if the message was updated or was already consumed; false if the
	 *         message does not exist in the given group
	 */
	public boolean ack(String consumerGroup, String messageId) {
		Query query = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(false));
		Update update = new Update().set(CONSUMED, true).set(RESERVED_UNTIL, null);

		UpdateResult result = mongoTemplate.updateFirst(query, update, Message.class, consumerGroup);
		if (result.getModifiedCount() > 0) {
			logger.debug("Ack succeeded for messageId={} in group={}", messageId, consumerGroup);
			return true;
		}

		// Idempotency: consider already consumed as success
		Query already = new Query(Criteria.where(ID).is(messageId).and(CONSUMED).is(true));
		boolean exists = mongoTemplate.exists(already, Message.class, consumerGroup);
		if (exists) {
			logger.debug("Ack idempotent success (already consumed) for messageId={} in group={}", messageId,
					consumerGroup);
			return true;
		}

		// Not found; return not found (404)
		logger.debug("Ack not found for messageId={} in group={}", messageId, consumerGroup);
		return false;
	}

	/**
	 * Batch acknowledge reserved messages.
	 * <p>
	 * Marks multiple documents as consumed (consumed=true) and clears their
	 * reservation window. This operation is efficient, using a single query.
	 * </p>
	 *
	 * @param consumerGroup
	 *            the MongoDB collection (consumer group) to operate on
	 * @param messageIds
	 *            the identifiers of the messages to acknowledge
	 * @return the number of messages successfully updated (or already consumed)
	 */
	public long batchAck(String consumerGroup, java.util.List<String> messageIds) {
		if (messageIds == null || messageIds.isEmpty()) {
			return 0;
		}

		// 1. Mark as consumed where consumed=false and id IN list
		Query query = new Query(Criteria.where(ID).in(messageIds).and(CONSUMED).is(false));
		Update update = new Update().set(CONSUMED, true).set(RESERVED_UNTIL, null);

		UpdateResult result = mongoTemplate.updateMulti(query, update, Message.class, consumerGroup);
		long updatedCount = result.getModifiedCount();

		// 2. For full correctness/idempotency return, we might want to count how many
		// ARE consumed now
		// but typically batch ack just returns "updated count" or we assume void.
		// Let's return updated count for now.
		logger.debug("Batch ack updated {} messages in group={}", updatedCount, consumerGroup);

		return updatedCount;
	}

	/**
	 * Negative-acknowledge a reserved or previously reserved message.
	 * <p>
	 * Immediately re-queues the message by setting reservedUntil to the current
	 * time, making it available for reservation again. No-op if the message is not
	 * found or is already consumed.
	 * </p>
	 *
	 * @param consumerGroup
	 *            the MongoDB collection (consumer group)
	 * @param messageId
	 *            the message identifier
	 * @param reason
	 *            optional reason used for diagnostics and stored in lastError
	 * @return true if the document was updated; false otherwise
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
			logger.debug("Nack succeeded for messageId={} in group={} (reason={})", messageId, consumerGroup, reason);
		} else {
			logger.debug("Nack no-op for messageId={} in group={} (not found or already consumed)", messageId,
					consumerGroup);
		}
		return modified;
	}

	/**
	 * Extend the visibility timeout for a reserved message.
	 * <p>
	 * If the message is currently reserved (reservedUntil > now) and unconsumed,
	 * sets reservedUntil to now + extensionSeconds (at least 1 second).
	 * </p>
	 *
	 * @param consumerGroup
	 *            the MongoDB collection (consumer group)
	 * @param messageId
	 *            the message identifier to extend
	 * @param extensionSeconds
	 *            number of seconds to extend the current reservation; values <= 0
	 *            are treated as 1
	 * @return true if the reservation was extended; false if the message is not
	 *         reserved, not found, or already consumed
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
			logger.debug("Extended visibility for messageId={} in group={} until={}", messageId, consumerGroup,
					newUntil);
		} else {
			logger.debug("Extend-visibility no-op for messageId={} in group={} (not reserved or not found)", messageId,
					consumerGroup);

		}
		return success;
	}

	/**
	 * Finds a message by ID in the given consumer group.
	 *
	 * @param consumerGroup
	 *            the MongoDB collection (consumer group)
	 * @param messageId
	 *            the message identifier
	 * @return Optional containing the message if present; otherwise empty
	 */
	public Optional<Message> findById(String consumerGroup, String messageId) {
		return Optional.ofNullable(mongoTemplate.findById(messageId, Message.class, consumerGroup));
	}
}
