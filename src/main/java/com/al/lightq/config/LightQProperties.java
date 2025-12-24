package com.al.lightq.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the LightQ application.
 * <p>
 * This class holds all the properties prefixed with {@code lightq}.
 * </p>
 */
@Validated
@Configuration
@ConfigurationProperties(prefix = "lightq")
public class LightQProperties {

	/**
	 * The maximum number of messages to fetch in a single view request.
	 */
	@Min(1)
	private int messageAllowedToFetch = 50;

	/**
	 * The duration in minutes for which messages are persisted in MongoDB.
	 */
	@Min(1)
	private int persistenceDurationMinutes = 30;

	/**
	 * The time-to-live in minutes for messages in the Redis cache.
	 */
	@Min(1)
	private int cacheTtlMinutes = 5;

	// Ack/Visibility/DLQ settings
	@Min(1)
	private int visibilityTimeoutSeconds = 30;

	@Min(1)
	private int maxDeliveryAttempts = 5;

	private String dlqSuffix = "-dlq";

	// Optional; null/absent means no TTL on DLQ collection
	private Integer dlqTtlMinutes;

	// Explicit getters and setters (replacing Lombok @Data)
	public LightQProperties() {
	}

	public int getMessageAllowedToFetch() {
		return messageAllowedToFetch;
	}

	public void setMessageAllowedToFetch(int messageAllowedToFetch) {
		this.messageAllowedToFetch = messageAllowedToFetch;
	}

	public int getPersistenceDurationMinutes() {
		return persistenceDurationMinutes;
	}

	public void setPersistenceDurationMinutes(int persistenceDurationMinutes) {
		this.persistenceDurationMinutes = persistenceDurationMinutes;
	}

	public int getCacheTtlMinutes() {
		return cacheTtlMinutes;
	}

	public void setCacheTtlMinutes(int cacheTtlMinutes) {
		this.cacheTtlMinutes = cacheTtlMinutes;
	}

	public int getVisibilityTimeoutSeconds() {
		return visibilityTimeoutSeconds;
	}

	public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) {
		this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
	}

	public int getMaxDeliveryAttempts() {
		return maxDeliveryAttempts;
	}

	public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
		this.maxDeliveryAttempts = maxDeliveryAttempts;
	}

	public String getDlqSuffix() {
		return dlqSuffix;
	}

	public void setDlqSuffix(String dlqSuffix) {
		this.dlqSuffix = dlqSuffix;
	}

	public Integer getDlqTtlMinutes() {
		return dlqTtlMinutes;
	}

	public void setDlqTtlMinutes(Integer dlqTtlMinutes) {
		this.dlqTtlMinutes = dlqTtlMinutes;
	}
}
