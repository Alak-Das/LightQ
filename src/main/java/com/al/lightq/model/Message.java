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
	 *            unique identifier
	 * @param content
	 *            message payload
	 * @param consumerGroup
	 *            consumer group (collection name)
	 * @param createdAt
	 *            creation timestamp
	 * @param consumed
	 *            consumed flag
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed) {
		this.id = id;
		this.content = content;
		this.consumerGroup = consumerGroup;
		this.createdAt = createdAt;
		this.consumed = consumed;
	}

	/**
	 * Full constructor including delivery/visibility/diagnostic fields.
	 *
	 * @param id
	 *            unique identifier
	 * @param content
	 *            message payload
	 * @param consumerGroup
	 *            consumer group (collection name)
	 * @param createdAt
	 *            creation timestamp
	 * @param consumed
	 *            consumed flag
	 * @param deliveryCount
	 *            number of times reserved
	 * @param reservedUntil
	 *            reservation expiry time
	 * @param lastDeliveryAt
	 *            last reservation timestamp
	 * @param lastError
	 *            last error message if any
	 */
	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed, int deliveryCount,
			Date reservedUntil, Date lastDeliveryAt, String lastError) {
		this.id = id;
		this.content = content;
		this.consumerGroup = consumerGroup;
		this.createdAt = createdAt;
		this.consumed = consumed;
		this.deliveryCount = deliveryCount;
		this.reservedUntil = reservedUntil;
		this.lastDeliveryAt = lastDeliveryAt;
		this.lastError = lastError;
	}

	public String getId() {
		return id;
	}

	public String getContent() {
		return content;
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

	public int getDeliveryCount() {
		return deliveryCount;
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

	/**
	 * Constructor for new messages.
	 *
	 * @param messageId
	 *            the message ID
	 * @param consumerGroup
	 *            the consumer group
	 * @param content
	 *            the message content
	 */
	public Message(String messageId, String consumerGroup, String content) {
		this(messageId, content, consumerGroup, new Date(), false);
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
				+ ", lastDeliveryAt=" + lastDeliveryAt + ", lastError='" + lastError + '\'' + '}';
	}
}
