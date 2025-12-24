package com.al.lightq.model;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a message in the queue.
 * <p>
 * This class is used to store the message content, consumer group, and other
 * metadata.
 * </p>
 */
@Document(collection = "messages-queue")
public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private String id;
	@NotBlank(message = "Message Content is mandatory")
	private String content;
	@NotBlank(message = "Message consumerGroup is mandatory")
	private String consumerGroup;
	private Date createdAt;
	private boolean consumed; // This flag is updated, so it cannot be final in this context
	// Acknowledgement and redelivery fields
	private int deliveryCount; // incremented on each reservation/pop
	private Date reservedUntil; // visibility timeout expiration
	private Date lastDeliveryAt; // last time it was handed out
	private String lastError; // optional reason from last nack or failure

	// Explicit constructors and getters (replacing Lombok)
	public Message() {
	}

	public Message(String id, String content, String consumerGroup, Date createdAt, boolean consumed) {
		this.id = id;
		this.content = content;
		this.consumerGroup = consumerGroup;
		this.createdAt = createdAt;
		this.consumed = consumed;
	}

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
