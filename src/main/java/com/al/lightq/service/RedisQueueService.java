package com.al.lightq.service;

import com.al.lightq.LightQConstants;
import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Redis cache access for LightQ.
 * <p>
 * Uses Redis Sorted Sets (ZSet) to store messages for idempotency and FIFO
 * ordering.
 * <ul>
 * <li>Score: System.currentTimeMillis() (approximate FIFO)</li>
 * <li>Idempotency: Attempts to add duplicate messages (same ID) are
 * no-ops.</li>
 * <li>Batch add: Uses pipelined ZADD operations.</li>
 * <li>Tail reads: Uses ZRANGE 0 to limit for FIFO retrieval.</li>
 * <li>Removal: Uses ZREM.</li>
 * </ul>
 * Keys are namespaced as "consumerGroupMessages:{group}". TTL, per-group
 * bounds, and other cache knobs are driven by LightQProperties.
 * </p>
 */
@Service
public class RedisQueueService {
	private static final Logger logger = LoggerFactory.getLogger(RedisQueueService.class);

	private final RedisTemplate<String, Message> redisTemplate;
	private final LightQProperties lightQProperties;

	// For tests to override TTL via
	// ReflectionTestUtils.setField("redisCacheTtlMinutes", ...)
	private long redisCacheTtlMinutes;

	public RedisQueueService(RedisTemplate<String, Message> redisTemplate, LightQProperties lightQProperties) {
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

		// Pipeline ZADD + ZREMRANGEBYRANK + EXPIRE
		redisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
			@Override
			@SuppressWarnings("unchecked")
			public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
				org.springframework.data.redis.core.RedisOperations<String, Message> ops = (org.springframework.data.redis.core.RedisOperations<String, Message>) operations;
				org.springframework.data.redis.core.ZSetOperations<String, Message> zset = ops.opsForZSet();

				// Use current time as score for FIFO
				zset.add(key, message, System.currentTimeMillis());

				int maxCacheEntries = Math.max(1,
						lightQProperties != null ? lightQProperties.getCacheMaxEntriesPerGroup() : 100);

				// Keep only oldest N items (remove ones with highest rank, i.e. newest if over
				// capacity)
				// Actually ZREMRANGEBYRANK removes by index. 0 is lowest score.
				// If we want to keep oldest (FIFO), we keep 0..N-1.
				// So we remove from N to -1.
				// Wait, if we want to keep most relevant? Usually cache keeps newest.
				// But queue is FIFO. So we want to keep the oldest messages (lowest scores).
				// New messages are added with current time (high score).
				// So if we trim, we should remove the NEWEST (highest score) if over capacity?
				// Typically queues drop oldest if full, but here cache is "front" of queue.
				// If cache is full, we probably want to stop adding or drop newest.
				// Actually, let's stick to simple "remove > N".
				// Newest messages have highest rank.
				// We want to keep 0..(max-1).
				// So we remove max..-1.

				zset.removeRange(key, maxCacheEntries, -1);

				ops.expire(key, Duration.ofMinutes(ttlMinutes));
				return null;
			}
		});
	}

	/**
	 * Pops a message from the cache (removes item with lowest score).
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @return the popped message, or null if no message was popped
	 */
	public Message popMessage(String consumerGroup) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		TypedTuple<Message> poppedTuple = redisTemplate.opsForZSet().popMin(key);
		Message popped = (poppedTuple != null) ? poppedTuple.getValue() : null;

		if (popped != null) {
			logger.debug("Cache hit (pop): key={}, messageId={}", key, popped.getId());
		} else {
			logger.debug("Cache miss (pop): key={}", key);
		}
		return popped;
	}

	/**
	 * Views messages in the cache (by score, low to high).
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @return the list of messages
	 */
	public List<Message> viewMessages(String consumerGroup) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		Set<Message> cachedObjects = redisTemplate.opsForZSet().range(key, 0, -1);
		if (cachedObjects == null || cachedObjects.isEmpty()) {
			logger.debug("Cache view: no entries for key={}", key);
			return Collections.emptyList();
		}
		logger.debug("Cache view: key={}, size={}", key, cachedObjects.size());
		// Set to List
		return new java.util.ArrayList<>(cachedObjects);
	}

	/**
	 * Views up to 'limit' oldest messages in the cache efficiently.
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @param limit
	 *            maximum number of messages to return (>=1)
	 * @return up to 'limit' messages from the head of the sorted set (lowest score)
	 */
	public List<Message> viewMessages(String consumerGroup, int limit) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		int safeLimit = Math.max(1, limit);
		// ZRANGE is 0-based inclusive. So to get N items, 0 to N-1.
		Set<Message> head = redisTemplate.opsForZSet().range(key, 0, safeLimit - 1);
		if (head == null || head.isEmpty()) {
			logger.debug("Cache view limited: no entries for key={}", key);
			return Collections.emptyList();
		}
		logger.debug("Cache view limited: key={}, size={}", key, head.size());
		return new java.util.ArrayList<>(head);
	}

	/**
	 * Batch add messages grouped by consumer group using pipelining and ZADD.
	 *
	 * @param messages
	 *            messages to add; ignored if null/empty
	 */
	public void addMessages(java.util.List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return;
		}
		final long ttlMinutes = (this.redisCacheTtlMinutes > 0)
				? this.redisCacheTtlMinutes
				: lightQProperties.getCacheTtlMinutes();
		final int maxCacheEntries = Math.max(1,
				lightQProperties != null ? lightQProperties.getCacheMaxEntriesPerGroup() : 100);

		// Group by consumerGroup
		final java.util.Map<String, java.util.List<Message>> byGroup = new java.util.HashMap<>();
		for (Message m : messages) {
			if (m == null || m.getConsumerGroup() == null) {
				continue;
			}
			byGroup.computeIfAbsent(m.getConsumerGroup(), k -> new java.util.ArrayList<>()).add(m);
		}
		if (byGroup.isEmpty()) {
			return;
		}

		redisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
			@Override
			@SuppressWarnings("unchecked")
			public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
				org.springframework.data.redis.core.RedisOperations<String, Message> ops = (org.springframework.data.redis.core.RedisOperations<String, Message>) operations;
				org.springframework.data.redis.core.ZSetOperations<String, Message> zset = ops.opsForZSet();

				for (java.util.Map.Entry<String, java.util.List<Message>> e : byGroup.entrySet()) {
					String key = LightQConstants.CACHE_PREFIX + e.getKey();
					java.util.List<Message> groupMsgs = e.getValue();

					// Prepare typed tuples
					long now = System.currentTimeMillis();
					Set<TypedTuple<Message>> tuples = groupMsgs.stream()
							.map(msg -> new DefaultTypedTuple<>(msg, (double) now)).collect(Collectors.toSet());

					zset.add(key, tuples);

					// Trim: keep 0 to max-1, remove max to -1
					zset.removeRange(key, maxCacheEntries, -1);

					ops.expire(key, java.time.Duration.ofMinutes(ttlMinutes));
				}
				return null;
			}
		});
	}

	/**
	 * Removes exactly one occurrence of the given message from the group's cache
	 * list. Uses Redis ZREM.
	 *
	 * @param consumerGroup
	 *            the consumer group
	 * @param message
	 *            the message instance to remove
	 * @return true if an element was removed
	 */
	public boolean removeOne(String consumerGroup, Message message) {
		String key = LightQConstants.CACHE_PREFIX + consumerGroup;
		Long removed = redisTemplate.opsForZSet().remove(key, message);
		boolean ok = removed != null && removed > 0;
		if (ok) {
			logger.debug("Cache removeOne: key={}, messageId={}", key, message.getId());
		} else {
			logger.debug("Cache removeOne miss: key={}, messageId={}", key, message.getId());
		}
		return ok;
	}
}
