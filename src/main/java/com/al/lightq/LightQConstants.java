package com.al.lightq;

/**
 * Constants used throughout the application.
 */
public class LightQConstants {

	private LightQConstants() {
		// Prevent instantiation
	}

	public static final String ID = "id";
	public static final String CONSUMED = "consumed";
	public static final String CREATED_AT = "createdAt";
	public static final String CONTENT = "content";
	public static final String CONSUMER_GROUP = "consumerGroup";
	public static final int CORE_POOL_SIZE = 5;
	public static final int MAX_POOL_SIZE = 10;
	public static final int QUEUE_CAPACITY = 25;
	public static final String THREAD_NAME_PREFIX = "DBDataUpdater-";
	public static final String QUEUE_BASE_URL = "/queue";
	public static final String PUSH_URL = "/push";
	public static final String POP_URL = "/pop";
	public static final String VIEW_URL = "/view";
	public static final String USER_ROLE = "USER";
	public static final String ADMIN_ROLE = "ADMIN";
	public static final String CONSUMER_GROUP_HEADER = "consumerGroup";
	public static final String MESSAGE_COUNT_HEADER = "messageCount";
	public static final String CACHE_PREFIX = "consumerGroupMessages:";
	public static final String INVALID_CONSUMER_GROUP_MESSAGE = "Invalid consumer group name.";
	public static final String EMPTY_MESSAGE_CONTENT_MESSAGE = "Message content cannot be empty";
	public static final String MESSAGE_SIZE_EXCEEDED_MESSAGE = "Message size cannot exceed 1MB";
	public static final String INVALID_CONSUMED_FILTER_MESSAGE = "Invalid consumed filter value received: {}. Expected 'yes' or 'no'. Ignoring filter.";
	public static final String NA = "N/A";
	public static final String YES = "yes";
	public static final String NO = "no";
	// Dead-letter queue metadata keys
	public static final String FAILED_AT = "failedAt";
	public static final String DLQ_REASON = "dlqReason";
	public static final String DLQ_REASON_MAX_DELIVERIES = "max-deliveries";

	// Acknowledgement & DLQ: field names
	public static final String RESERVED_UNTIL = "reservedUntil";
	public static final String DELIVERY_COUNT = "deliveryCount";
	public static final String LAST_DELIVERY_AT = "lastDeliveryAt";
	public static final String LAST_ERROR = "lastError";

	// API endpoints for ack/Nack/visibility and DLQ
	public static final String ACK_URL = "/ack";
	public static final String NACK_URL = "/nack";
	public static final String EXTEND_VIS_URL = "/extend-visibility";
	public static final String DLQ_VIEW_URL = "/dlq/view";
	public static final String DLQ_REPLAY_URL = "/dlq/replay";
}
