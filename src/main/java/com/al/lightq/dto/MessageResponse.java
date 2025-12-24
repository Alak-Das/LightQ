package com.al.lightq.dto;

import com.al.lightq.model.Message;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Represents a response containing a message.
 * <p>
 * This class is used to create a consistent response format for messages.
 * </p>
 */
public class MessageResponse {
	private String id;
	private String content;
	private LocalDateTime createdAt;

	public MessageResponse() {
	}

	public String getId() {
		return id;
	}

	public String getContent() {
		return content;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public MessageResponse(Message message) {
		this.id = message.getId();
		this.content = message.getContent();
		java.util.Date date = message.getCreatedAt();
		this.createdAt = (date != null) ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
	}
}
