package com.al.lightq.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

/**
 * Represents a message in the queue.
 * <p>
 * This class is used to store the message content, consumer group, and other metadata.
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages-queue")
public class Message implements Serializable {

    @Id
    private String id;
    @NotBlank(message = "Message Content is mandatory")
    private String content;
    @NotBlank(message = "Message consumerGroup is mandatory")
    private String consumerGroup;
    private Date createdAt;
    private boolean consumed; // This flag is updated, so it cannot be final in this context

    /**
     * Constructor for new messages.
     *
     * @param messageId     the message ID
     * @param consumerGroup the consumer group
     * @param content       the message content
     */
    public Message(String messageId, String consumerGroup, String content) {
        this(messageId, content, consumerGroup, new Date(), false);
    }

    /**
     * Creates a new Message instance with updated consumed status.
     *
     * @return a new Message instance with the consumed flag set to true
     */
    public Message markConsumed() {
        return new Message(this.id, this.content, this.consumerGroup, this.createdAt, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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
}
