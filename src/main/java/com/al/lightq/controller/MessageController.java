package com.al.lightq.controller;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.dto.MessageResponse;
import com.al.lightq.model.Message;
import com.al.lightq.service.AcknowledgementService;
import com.al.lightq.service.DlqService;
import com.al.lightq.service.PopMessageService;
import com.al.lightq.service.PushMessageService;
import com.al.lightq.service.ViewMessageService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for LightQ message queue APIs.
 * <p>
 * Base path: /queue. Exposes endpoints for:
 * <ul>
 * <li>Push: POST /queue/push</li>
 * <li>Batch Push: POST /queue/batch/push</li>
 * <li>Pop (reserve): GET /queue/pop</li>
 * <li>Ack/Nack/Extend visibility: POST /queue/ack, /queue/nack,
 * /queue/extend-visibility</li>
 * <li>View (admin): GET /queue/view</li>
 * <li>DLQ view/replay (admin): GET /queue/dlq/view, POST /queue/dlq/replay</li>
 * </ul>
 * Delivery semantics are at-least-once via reservation (visibility timeout)
 * followed by explicit acknowledgement.
 * </p>
 */
@RestController
@RequestMapping(QUEUE_BASE_URL)
public class MessageController {

	private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

	private final PushMessageService pushMessageService;
	private final PopMessageService popMessageService;
	private final ViewMessageService viewMessageService;
	private final LightQProperties lightQProperties;
	private final AcknowledgementService acknowledgementService;
	private final DlqService dlqService;

	public MessageController(PushMessageService pushMessageService, PopMessageService popMessageService,
			ViewMessageService viewMessageService, LightQProperties lightQProperties,
			AcknowledgementService acknowledgementService, DlqService dlqService) {
		this.pushMessageService = pushMessageService;
		this.popMessageService = popMessageService;
		this.viewMessageService = viewMessageService;
		this.lightQProperties = lightQProperties;
		this.acknowledgementService = acknowledgementService;
		this.dlqService = dlqService;
	}

	/**
	 * Pushes a new message to the queue for a specific consumer group.
	 *
	 * @param consumerGroup
	 *            The header indicating the consumer group for the message.
	 * @param content
	 *            The content of the message to be pushed.
	 * @return A {@link MessageResponse} containing details of the pushed message.
	 */
	@PostMapping(PUSH_URL)
	public MessageResponse push(
			@RequestHeader(CONSUMER_GROUP_HEADER) @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = INVALID_CONSUMER_GROUP_MESSAGE) String consumerGroup,

			@NotBlank(message = EMPTY_MESSAGE_CONTENT_MESSAGE) @Size(max = 1048576, message = MESSAGE_SIZE_EXCEEDED_MESSAGE) @RequestBody String content) {
		int contentLength = content != null ? content.length() : 0;
		logger.debug("Received push request for consumer group: {} with contentLength={} chars", consumerGroup,
				contentLength);
		Message message = new Message(UUID.randomUUID().toString(), consumerGroup, content);
		Message pushedMessage = pushMessageService.push(message);
		logger.debug("Message with ID {} pushed to consumer group {}", pushedMessage.getId(), consumerGroup);
		return new MessageResponse(pushedMessage);
	}

	/**
	 * Batch push endpoint to reduce per-message overhead. Accepts a JSON array of
	 * string contents and creates messages for the provided consumer group. Adds
	 * all to cache using a single Redis pipeline (LPUSHALL per group) and
	 * asynchronously persists to MongoDB in groups.
	 *
	 * Request: POST /queue/batch/push Headers: consumerGroup Body: ["content-1",
	 * "content-2", ...]
	 *
	 * Response: 200 OK with an array of MessageResponse containing the created
	 * messages. 400 if the body is empty.
	 */
	@PostMapping(BATCH_PUSH_URL)
	public ResponseEntity<java.util.List<MessageResponse>> batchPush(
			@RequestHeader(CONSUMER_GROUP_HEADER) @Pattern(regexp = "^[a-zA-Z0-9-_]{1,50}$", message = INVALID_CONSUMER_GROUP_MESSAGE) String consumerGroup,
			@RequestBody java.util.List<@NotBlank(message = EMPTY_MESSAGE_CONTENT_MESSAGE) @Size(max = 1048576, message = MESSAGE_SIZE_EXCEEDED_MESSAGE) String> contents) {
		if (contents == null || contents.isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		logger.debug("Received batch push request for consumer group: {} with {} messages", consumerGroup,
				contents.size());
		java.util.List<Message> messages = new java.util.ArrayList<>(contents.size());
		for (String c : contents) {
			messages.add(new Message(UUID.randomUUID().toString(), consumerGroup, c));
		}
		java.util.List<Message> pushed = pushMessageService.pushBatch(messages);
		java.util.List<MessageResponse> response = pushed.stream().map(MessageResponse::new).toList();
		return ResponseEntity.ok(response);
	}

	/**
	 * Pops the oldest available message for a specific consumer group.
	 *
	 * @param consumerGroup
	 *            The header indicating the consumer group from which to pop the
	 *            message.
	 * @return A {@link ResponseEntity} containing a {@link MessageResponse} if a
	 *         message is found, or a not found response if the queue is empty.
	 */
	@GetMapping(POP_URL)
	public ResponseEntity<MessageResponse> pop(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup) {
		logger.debug("Received pop request for consumer group: {}", consumerGroup);
		Optional<Message> message = popMessageService.pop(consumerGroup);
		if (message.isPresent()) {
			logger.debug("Message with ID {} popped from consumer group {}", message.get().getId(), consumerGroup);
		} else {
			logger.debug("No message found to pop for consumer group {}", consumerGroup);
		}
		return message.map(msg -> ResponseEntity.ok(new MessageResponse(msg)))
				.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * Views messages in the queue for a specific consumer group, with optional
	 * filtering by consumption status.
	 *
	 * @param consumerGroup
	 *            The header indicating the consumer group to view messages from.
	 * @param messageCount
	 *            The maximum number of messages to retrieve.
	 * @param consumed
	 *            Optional header to filter messages by consumption status ("yes"
	 *            for consumed, "no" for unconsumed).
	 * @return A {@link ResponseEntity} containing a list of {@link Message}
	 *         objects.
	 */
	@GetMapping(VIEW_URL)
	public ResponseEntity<List<Message>> view(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestHeader(value = MESSAGE_COUNT_HEADER, required = false) Integer messageCount,
			@RequestHeader(value = CONSUMED, required = false) String consumed) {
		logger.debug("Received view request for consumer group: {}, message count: {}, consumed status: {}",
				consumerGroup, messageCount, StringUtils.isEmpty(consumed) ? NA : consumed);
		if (StringUtils.isNotEmpty(consumed) && !YES.equalsIgnoreCase(consumed) && !NO.equalsIgnoreCase(consumed)) {
			logger.warn(INVALID_CONSUMED_FILTER_MESSAGE, consumed);
		}

		int limit = resolveLimit(messageCount);

		List<Message> messages = viewMessageService.view(consumerGroup, limit, consumed);
		logger.debug("Returning {} messages for consumer group: {}, filtered by consumed status: {}", messages.size(),
				consumerGroup, StringUtils.isEmpty(consumed) ? NA : consumed);
		return ResponseEntity.ok(messages);
	}

	// Acknowledgement endpoints

	/**
	 * Acknowledge a reserved message. Marks the message as consumed and clears its
	 * reservation (reservedUntil=null). Idempotent: returns 200 even if the message
	 * was already consumed. Returns 404 if the message does not exist in the given
	 * consumer group.
	 *
	 * Endpoint: POST /queue/ack Headers: - consumerGroup: target consumer group
	 * (required) Query Parameters: - id: message identifier (required) Security:
	 * USER or ADMIN
	 *
	 * @param consumerGroup
	 *            The consumer group header.
	 * @param messageId
	 *            The message identifier to acknowledge.
	 * @return 200 OK on success; 404 Not Found if the message does not exist.
	 */
	@PostMapping(ACK_URL)
	public ResponseEntity<Void> ack(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestParam("id") String messageId) {
		logger.debug("Received ack request for consumer group: {}, messageId: {}", consumerGroup, messageId);
		boolean ok = acknowledgementService.ack(consumerGroup, messageId);
		return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
	}

	/**
	 * Negative acknowledgement for a reserved message. Immediately re-queues the
	 * message by setting reservedUntil to now. Optionally records a reason for
	 * diagnostics. No-op if the message is not found or already consumed.
	 *
	 * Endpoint: POST /queue/nack Headers: - consumerGroup: target consumer group
	 * (required) Query Parameters: - id: message identifier (required) - reason:
	 * optional reason for the nack Security: USER or ADMIN
	 *
	 * @param consumerGroup
	 *            The consumer group header.
	 * @param messageId
	 *            The message identifier to negative-ack.
	 * @param reason
	 *            Optional reason describing the nack cause.
	 * @return 200 OK if updated; 404 Not Found / no-op otherwise.
	 */
	@PostMapping(NACK_URL)
	public ResponseEntity<Void> nack(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestParam("id") String messageId, @RequestParam(value = "reason", required = false) String reason) {
		logger.debug("Received nack request for consumer group: {}, messageId: {}, reason: {}", consumerGroup,
				messageId, StringUtils.isEmpty(reason) ? NA : reason);
		boolean ok = acknowledgementService.nack(consumerGroup, messageId, reason);
		return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
	}

	/**
	 * Extend the visibility timeout for a reserved message. Updates reservedUntil
	 * to now + provided seconds if the message is currently reserved and
	 * unconsumed.
	 *
	 * Endpoint: POST /queue/extend-visibility Headers: - consumerGroup: target
	 * consumer group (required) Query Parameters: - id: message identifier
	 * (required) - seconds: extension window in seconds (required, >=1 recommended)
	 * Security: USER or ADMIN
	 *
	 * @param consumerGroup
	 *            The consumer group header.
	 * @param messageId
	 *            The message identifier to extend.
	 * @param seconds
	 *            The number of seconds to extend the current reservation.
	 * @return 200 OK if extended; 400 Bad Request if not currently reserved or not
	 *         found.
	 */
	@PostMapping(EXTEND_VIS_URL)
	public ResponseEntity<Void> extendVisibility(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestParam("id") String messageId, @RequestParam("seconds") int seconds) {
		logger.debug("Received extend-visibility request for consumer group: {}, messageId: {}, seconds: {}",
				consumerGroup, messageId, seconds);
		boolean ok = acknowledgementService.extendVisibility(consumerGroup, messageId, seconds);
		return ok ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
	}

	// DLQ endpoints

	/**
	 * View entries for the Dead Letter Queue (DLQ) of a consumer group. Returns
	 * recent DLQ documents up to the provided limit (defaults to
	 * lightq.message-allowed-to-fetch).
	 *
	 * Endpoint: GET /queue/dlq/view Headers: - consumerGroup: target consumer group
	 * (required) Query Parameters: - limit: max entries to return (optional)
	 * Security: ADMIN
	 *
	 * @param consumerGroup
	 *            The consumer group whose DLQ is being viewed.
	 * @param limit
	 *            Optional limit for number of DLQ entries returned.
	 * @return 200 OK with a list of DLQ documents.
	 */
	@GetMapping(DLQ_VIEW_URL)
	public ResponseEntity<List<Document>> dlqView(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestParam(value = "limit", required = false) Integer limit) {
		int lim = resolveLimit(limit);
		logger.debug("Received DLQ view request for consumer group: {}, limit: {}", consumerGroup, lim);
		return ResponseEntity.ok(dlqService.view(consumerGroup, lim));
	}

	/**
	 * Replay selected DLQ entries back into the main queue and cache. Removes the
	 * specified entries from the DLQ after reinsertion.
	 *
	 * Endpoint: POST /queue/dlq/replay Headers: - consumerGroup: target consumer
	 * group (required) Body: - JSON array of message IDs to replay Security: ADMIN
	 *
	 * @param consumerGroup
	 *            The consumer group whose DLQ entries should be replayed.
	 * @param ids
	 *            The list of message IDs to replay.
	 * @return 200 OK with the count of messages successfully replayed.
	 */
	@PostMapping(DLQ_REPLAY_URL)
	public ResponseEntity<Integer> dlqReplay(@RequestHeader(CONSUMER_GROUP_HEADER) String consumerGroup,
			@RequestBody List<String> ids) {
		logger.debug("Received DLQ replay request for consumer group: {} with {} ids", consumerGroup,
				ids == null ? 0 : ids.size());
		int count = dlqService.replay(consumerGroup, ids);
		return ResponseEntity.ok(count);
	}

	/**
	 * Resolves an effective limit for result size.
	 * <p>
	 * If a positive value is provided, caps it by the configured
	 * lightq.message-allowed-to-fetch. Otherwise returns the configured default.
	 * </p>
	 *
	 * @param requested
	 *            optional client-provided limit
	 * @return a positive limit not exceeding the configured maximum
	 */
	private int resolveLimit(Integer requested) {
		return (requested != null && requested > 0)
				? Math.min(requested, lightQProperties.getMessageAllowedToFetch())
				: lightQProperties.getMessageAllowedToFetch();
	}
}
