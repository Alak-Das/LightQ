package com.al.lightq.dto;

import java.time.LocalDateTime;

/**
 * Represents a standard error response.
 * <p>
 * This class is used to create a consistent error response format across the
 * application.
 * </p>
 */
public class ErrorResponse {
	private LocalDateTime timestamp;
	private int status;
	private String error;
	private String message;
	private String path;
	private String requestId;

	// Explicit getters and setters (replacing Lombok @Data)
	public java.time.LocalDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(java.time.LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	public ErrorResponse(int status, String error, String message, String path) {
		this.timestamp = LocalDateTime.now();
		this.status = status;
		this.error = error;
		this.message = message;
		this.path = path;
	}

	public ErrorResponse(int status, String error, String message, String path, String requestId) {
		this.timestamp = LocalDateTime.now();
		this.status = status;
		this.error = error;
		this.message = message;
		this.path = path;
		this.requestId = requestId;
	}
}
