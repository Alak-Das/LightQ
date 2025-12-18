package com.al.lightq.util;

/**
 * Constants used throughout the application.
 */
public class LightQConstants {

    public static final String ID = "id";
    public static final String CONSUMED = "consumed";
    public static final String CREATED_AT = "createdAt";
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
    public static final String HAS_ADMIN_ROLE = "hasRole(\'ADMIN\')";
    public static final String CONSUMER_GROUP_HEADER = "consumerGroup";
    public static final String MESSAGE_COUNT_HEADER = "messageCount";
    public static final String MESSAGE_COUNT_VALIDATION_ERROR_MESSAGE = "Message Count should be 1 to %s.";
    public static final String CACHE_PREFIX = "consumerGroupMessages:";
    public static final String INVALID_CONSUMER_GROUP_MESSAGE = "Invalid consumer group name.";
    public static final String EMPTY_MESSAGE_CONTENT_MESSAGE = "Message content cannot be empty";
    public static final String MESSAGE_SIZE_EXCEEDED_MESSAGE = "Message size cannot exceed 1MB";
    public static final String INVALID_CONSUMED_FILTER_MESSAGE = "Invalid consumed filter value received: {}. Expected 'yes' or 'no'. Ignoring filter.";
    public static final String NA = "N/A";
    public static final String YES = "yes";
    public static final String NO = "no";
}
