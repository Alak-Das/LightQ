package com.al.lightq.model;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import org.springframework.data.annotation.Id;

/**
 * Represents a message in the queue.
 * <p>
 * Stored in MongoDB per consumer group collection; operations target the
 * collection named after the consumer group via MongoTemplate. Includes fields
 * for reservation, visibility, delivery tracking, and diagnostics.
 * </p>
 */
public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Unique message identifier (UUID string). */
	@Id
	private String id;
	/** Application-defined message payload. Must be non-blank. */
	@NotBlank(message = "Message Content is mandatory")
	private String content;
	/** Target consumer group (also MongoDB collection name). Must be non-blank. */
	@NotBlank(message = "Message consumerGroup is mandatory")
	private String consumerGroup;
	/** Creation timestamp (UTC on server). Set when the message is created. */
	private Date createdAt;
	/** Whether the message has been acknowledged (removed from active delivery). */
	private boolean consumed; // This flag is updated, so it cannot be final in this context
	// Acknowledgement and redelivery fields
	/** Number of times the message has been reserved (incremented on each pop). */
	private int deliveryCount; // incremented on each reservation/pop
	/** Reservation expiry time for visibility timeout; null when not reserved. */
	private Date reservedUntil; // visibility timeout expiration
	/** Timestamp of the most recent successful reservation. */
	private Date lastDeliveryAt; // last time it was handed out
	/** Optional reason from last negative acknowledgement or processing failure. */
	private String lastError; // optional reason from last nack or failure
	/** Priority level (0-10), higher is more urgent. Default 0. */
	private int priority;
	/** Time when the message becomes visible for processing. */
	private Date scheduledAt;
	/** Indicates if the content is compressed (GZIP + Base64). */
	private boolean compressed;

	// Explicit constructors and getters (replacing Lombok)
	/**
	 * Default constructor for frameworks and deserialization.
	 */
	public Message() {
	}

	/**
	 * Full constructor for core fields.
	 *
	 * @param id
	 *                      unique identifier
	 * @param content
	 *                      message payload
	 * @param consumerGroup
	 *                      consumer group (collection name)
	 * @param createdAt
	 *                      creation timestamp
	 * @param consumed
	 *                      consumed flag
	 * @param scheduledAt
	 *                      time when the message becomes visible
	 * @param priority
	 *                      priority level
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed,
			Date scheduledAt, int priority) {
		this.id = id;
		this.content = content;
		this.consumerGroup = consumerGroup;
		this.createdAt = createdAt;
		this.consumed = consumed;
		this.scheduledAt = scheduledAt;
		this.priority = priority;
	}

	/**
	 * Full constructor including delivery/visibility/diagnostic fields.
	 *
	 * @param id
	 *                       unique identifier
	 * @param content
	 *                       message payload
	 * @param consumerGroup
	 *                       consumer group (collection name)
	 * @param createdAt
	 *                       creation timestamp
	 * @param consumed
	 *                       consumed flag
	 * @param deliveryCount
	 *                       number of times reserved
	 * @param reservedUntil
	 *                       reservation expiry time
	 * @param lastDeliveryAt
	 *                       last reservation timestamp
	 * @param lastError
	 *                       last error message if any
	 * @param lastError
	 *                       last error message if any
	 * @param scheduledAt
	 *                       time when the message becomes visible
	 * @param priority
	 *                       priority level
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed, int deliveryCount,
			Date reservedUntil, Date lastDeliveryAt, String lastError, Date scheduledAt, int priority) {
		this.id = id;
		this.content = content;
		this.consumerGroup = consumerGroup;
		this.createdAt = createdAt;
		this.consumed = consumed;
		this.deliveryCount = deliveryCount;
		this.reservedUntil = reservedUntil;
		this.lastDeliveryAt = lastDeliveryAt;
		this.lastError = lastError;
		this.scheduledAt = scheduledAt;
		this.priority = priority;
	}

	public String getId() {
		return id;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getConsumerGroup() {
		return consumerGroup;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public boolean isConsumed() {
		return consumed;
	}

	public void setConsumed(boolean consumed) {
		this.consumed = consumed;
	}

	public int getDeliveryCount() {
		return deliveryCount;
	}

	public void setDeliveryCount(int deliveryCount) {
		this.deliveryCount = deliveryCount;
	}

	public Date getReservedUntil() {
		return reservedUntil;
	}

	public Date getLastDeliveryAt() {
		return lastDeliveryAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Date getScheduledAt() {
		return scheduledAt;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public boolean isCompressed() {
		return compressed;
	}

	public void setCompressed(boolean compressed) {
		this.compressed = compressed;
	}

	/**
	 * Constructor for new messages.
	 *
	 * @param messageId
	 *                      the message ID
	 * @param consumerGroup
	 *                      the consumer group
	 * @param content
	 *                      the message content
	 * @param scheduledAt
	 *                      optional time when the message becomes visible
	 * @param priority
	 *                      priority level (0-10)
	 */

	public Message(String messageId, String consumerGroup, String content, Date scheduledAt, int priority) {
		this(messageId, content, consumerGroup, new Date(), false, scheduledAt, priority);
	}

	/**
	 * Constructor for new messages (immediate delivery).
	 *
	 * @param messageId
	 *                      the message ID
	 * @param consumerGroup
	 *                      the consumer group
	 * @param content
	 *                      the message content
	 * @param priority
	 *                      priority level (0-10)
	 */
	public Message(String messageId, String consumerGroup, String content, int priority) {
		this(messageId, content, consumerGroup, new Date(), false, null, priority);
	}

	/**
	 * Constructor for new messages (immediate delivery, default priority).
	 *
	 * @param messageId
	 *                      the message ID
	 * @param consumerGroup
	 *                      the consumer group
	 * @param content
	 *                      the message content
	 */
	public Message(String messageId, String consumerGroup, String content) {
		this(messageId, content, consumerGroup, new Date(), false, null, 0);
	}

	/**
	 * Legacy full constructor (for backward compatibility).
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed) {
		this(id, content, consumerGroup, createdAt, consumed, null, 0);
	}

	/**
	 * Legacy full constructor including delivery/visibility/diagnostic fields (for
	 * backward compatibility).
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed, int deliveryCount,
			Date reservedUntil, Date lastDeliveryAt, String lastError) {
		this(id, content, consumerGroup, createdAt, consumed, deliveryCount, reservedUntil, lastDeliveryAt, lastError,
				null, 0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Message message = (Message) o;
		return Objects.equals(id, message.id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "Message{" + "id='" + id + '\'' + ", consumerGroup='" + consumerGroup + '\'' + ", createdAt=" + createdAt
				+ ", consumed=" + consumed + ", deliveryCount=" + deliveryCount + ", reservedUntil=" + reservedUntil
				+ ", lastDeliveryAt=" + lastDeliveryAt + ", lastError='" + lastError + '\'' + ", scheduledAt="
				+ scheduledAt + ", priority=" + priority + '}';
	}
}
