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

	/**
	 * Constructs a response DTO from a domain Message.
	 * <p>
	 * Converts the Message createdAt (java.util.Date) to LocalDateTime using the
	 * system default time zone.
	 * </p>
	 *
	 * @param message
	 *            source domain message
	 */
	public MessageResponse(Message message) {
		this.id = message.getId();
		this.content = message.getContent();
		java.util.Date date = message.getCreatedAt();
		this.createdAt = (date != null) ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
	}
}
