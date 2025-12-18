package com.al.lightq.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Represents a response containing a message.
 * <p>
 * This class is used to create a consistent response format for messages.
 * </p>
 */
@Data
@NoArgsConstructor
public class MessageResponse {
    private String id;
    private String content;
    private LocalDateTime createdAt;

    public MessageResponse(Message message) {
        this.id = message.getId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
