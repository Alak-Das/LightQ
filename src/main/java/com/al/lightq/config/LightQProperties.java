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

	// Performance/scalability: cache bounds per consumer group
	@Min(1)
	private int cacheMaxEntriesPerGroup = 100;

	// Performance/scalability: bounded cache to track ensured indexes per group
	@Min(1)
	private int indexCacheMaxGroups = 1000;

	@Min(1)
	private int indexCacheExpireMinutes = 60;

	// Async executor tuning
	@Min(1)
	private int corePoolSize = 5;

	@Min(1)
	private int maxPoolSize = 10;

	@Min(1)
	private int queueCapacity = 25;

	private String threadNamePrefix = "DBDataUpdater-";

	private boolean allowCoreThreadTimeout = true;

	@Min(0)
	private int awaitTerminationSeconds = 30;

	// Redis client tuning (Lettuce)
	@Min(1)
	private int redisCommandTimeoutSeconds = 5;

	@Min(0)
	private int redisShutdownTimeoutSeconds = 2;

	// Redis pooling (Lettuce + commons-pool2)
	@Min(0)
	private int redisPoolMaxTotal = 64;

	@Min(0)
	private int redisPoolMaxIdle = 32;

	@Min(0)
	private int redisPoolMinIdle = 8;

	// Async persistence feature flag
	private boolean asyncPersistence = false;

	// Explicit getters and setters (replacing Lombok @Data)
	public LightQProperties() {
	}

	public boolean isAsyncPersistence() {
		return asyncPersistence;
	}

	public void setAsyncPersistence(boolean asyncPersistence) {
		this.asyncPersistence = asyncPersistence;
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

	public int getCacheMaxEntriesPerGroup() {
		return cacheMaxEntriesPerGroup;
	}

	public void setCacheMaxEntriesPerGroup(int cacheMaxEntriesPerGroup) {
		this.cacheMaxEntriesPerGroup = cacheMaxEntriesPerGroup;
	}

	public int getIndexCacheMaxGroups() {
		return indexCacheMaxGroups;
	}

	public void setIndexCacheMaxGroups(int indexCacheMaxGroups) {
		this.indexCacheMaxGroups = indexCacheMaxGroups;
	}

	public int getIndexCacheExpireMinutes() {
		return indexCacheExpireMinutes;
	}

	public void setIndexCacheExpireMinutes(int indexCacheExpireMinutes) {
		this.indexCacheExpireMinutes = indexCacheExpireMinutes;
	}

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public void setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	public int getMaxPoolSize() {
		return maxPoolSize;
	}

	public void setMaxPoolSize(int maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	public int getQueueCapacity() {
		return queueCapacity;
	}

	public void setQueueCapacity(int queueCapacity) {
		this.queueCapacity = queueCapacity;
	}

	public String getThreadNamePrefix() {
		return threadNamePrefix;
	}

	public void setThreadNamePrefix(String threadNamePrefix) {
		this.threadNamePrefix = threadNamePrefix;
	}

	public boolean isAllowCoreThreadTimeout() {
		return allowCoreThreadTimeout;
	}

	public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
		this.allowCoreThreadTimeout = allowCoreThreadTimeout;
	}

	public int getAwaitTerminationSeconds() {
		return awaitTerminationSeconds;
	}

	public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
		this.awaitTerminationSeconds = awaitTerminationSeconds;
	}

	public int getRedisCommandTimeoutSeconds() {
		return redisCommandTimeoutSeconds;
	}

	public void setRedisCommandTimeoutSeconds(int redisCommandTimeoutSeconds) {
		this.redisCommandTimeoutSeconds = redisCommandTimeoutSeconds;
	}

	public int getRedisShutdownTimeoutSeconds() {
		return redisShutdownTimeoutSeconds;
	}

	public void setRedisShutdownTimeoutSeconds(int redisShutdownTimeoutSeconds) {
		this.redisShutdownTimeoutSeconds = redisShutdownTimeoutSeconds;
	}

	public int getRedisPoolMaxTotal() {
		return redisPoolMaxTotal;
	}

	public void setRedisPoolMaxTotal(int redisPoolMaxTotal) {
		this.redisPoolMaxTotal = redisPoolMaxTotal;
	}

	public int getRedisPoolMaxIdle() {
		return redisPoolMaxIdle;
	}

	public void setRedisPoolMaxIdle(int redisPoolMaxIdle) {
		this.redisPoolMaxIdle = redisPoolMaxIdle;
	}

	public int getRedisPoolMinIdle() {
		return redisPoolMinIdle;
	}

	public void setRedisPoolMinIdle(int redisPoolMinIdle) {
		this.redisPoolMinIdle = redisPoolMinIdle;
	}
}
