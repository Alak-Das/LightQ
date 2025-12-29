package com.al.lightq.service;

import com.al.lightq.LightQConstants;
import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for interacting with the Redis cache.
 * <p>
 * This class provides methods for adding, popping, and viewing messages in the
 * cache.
 * </p>
 */
@Service
public class CacheService {
	private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

	private final RedisTemplate<String, Message> redisTemplate;
	private final LightQProperties lightQProperties;

	// For tests to override TTL via
	// ReflectionTestUtils.setField("redisCacheTtlMinutes", ...)
	private long redisCacheTtlMinutes;

	public CacheService(RedisTemplate<String, Message> redisTemplate, LightQProperties lightQProperties) {
		this.redisTemplate = redisTemplate;
		this.lightQProperties = lightQProperties;
	}

	/**
	 * Adds a message to the cache.
	 *
	 * @param message
	 *            the message to add
	 */
	public void addMessage(Message message) {
		String key = LightQConstants.CACHE_PREFIX + message.getConsumerGroup();
		long ttlMinutes = (this.redisCacheTtlMinutes > 0)
				? this.redisCacheTtlMinutes
				: lightQProperties.getCacheTtlMinutes();
		logger.debug("Cache add: key={}, messageId={}, ttlMinutes={}", key, message.getId(), ttlMinutes);

		// Pipeline LPUSH + LTRIM + EXPIRE to minimize round-trips
		redisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
			@Override
			@SuppressWarnings("unchecked")
			public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
				org.springframework.data.redis.core.RedisOperations<String, Message> ops = (org.springframework.data.redis.core.RedisOperations<String, Message>) operations;
				org.springframework.data.redis.core.ListOperations<String, Message> list = ops.opsForList();

				list.leftPush(key, message);

				int maxCacheEntries = Math.max(1,
						lightQProperties != null ? lightQProperties.getCacheMaxEntriesPerGroup() : 100);
				list.trim(key, 0, maxCacheEntries - 1);

				ops.expire(key, Duration.ofMinutes(ttlMinutes));
				return null;
			}
		});
	}

	/**
	 * Pops a message from the cache.
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @return the popped message, or null if no message was popped
	 */
	public Message popMessage(String consumerGroup) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		Message popped = redisTemplate.opsForList().rightPop(key);
		if (popped != null) {
			logger.debug("Cache hit (pop): key={}, messageId={}", key, popped.getId());
		} else {
			logger.debug("Cache miss (pop): key={}", key);
		}
		return popped;
	}

	/**
	 * Views messages in the cache.
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @return the list of messages
	 */
	public List<Message> viewMessages(String consumerGroup) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		List<Message> cachedObjects = redisTemplate.opsForList().range(key, 0, -1);
		if (cachedObjects == null || cachedObjects.isEmpty()) {
			logger.debug("Cache view: no entries for key={}", key);
			return Collections.emptyList();
		}
		logger.debug("Cache view: key={}, size={}", key, cachedObjects.size());
		return cachedObjects;
	}

	/**
	 * Views up to 'limit' oldest messages in the cache efficiently.
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @param limit
	 *            maximum number of messages to return (>=1)
	 * @return up to 'limit' messages from the tail of the list
	 */
	public List<Message> viewMessages(String consumerGroup, int limit) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		int safeLimit = Math.max(1, limit);
		List<Message> tail = redisTemplate.opsForList().range(key, -safeLimit, -1);
		if (tail == null || tail.isEmpty()) {
			logger.debug("Cache view limited: no entries for key={}", key);
			return Collections.emptyList();
		}
		logger.debug("Cache view limited: key={}, size={}", key, tail.size());
		return tail;
	}
}
