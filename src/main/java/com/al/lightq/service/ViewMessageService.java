package com.al.lightq.service;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.model.Message;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Service for viewing messages in the queue for a specific consumer group.
 * <p>
 * It retrieves messages from both cache and MongoDB, combines them, and applies
 * filtering and sorting.
 * </p>
 */
@Service
public class ViewMessageService {

	private static final Logger logger = LoggerFactory.getLogger(ViewMessageService.class);
	private final MongoTemplate mongoTemplate;
	private final CacheService cacheService;

	public ViewMessageService(MongoTemplate mongoTemplate, CacheService cacheService) {
		this.mongoTemplate = mongoTemplate;
		this.cacheService = cacheService;
	}

	/**
	 * Retrieves messages for a consumer group with optional consumption filter and
	 * count limit.
	 * <p>
	 * Behavior:
	 * <ul>
	 * <li>If consumed is "yes" (case-insensitive): fetches only from MongoDB where
	 * consumed=true.</li>
	 * <li>If consumed is "no": returns unconsumed messages, preferring cache, then
	 * fills from MongoDB.</li>
	 * <li>If consumed is null/blank/any other value: returns unfiltered messages,
	 * preferring cache then DB.</li>
	 * </ul>
	 * Cache results are de-duplicated against DB results by message id. Results are
	 * sorted by createdAt ascending and truncated to at most limit entries.
	 * </p>
	 *
	 * @param consumerGroup
	 *            the consumer group (MongoDB collection) to read from
	 * @param limit
	 *            maximum number of messages to return; if less than 1, the
	 *            service's default limit is applied by caller
	 * @param consumed
	 *            optional flag "yes" or "no" controlling filtering semantics (see
	 *            above)
	 * @return a list (size <= limit) of messages sorted by createdAt ascending
	 */
	public List<Message> view(String consumerGroup, int limit, String consumed) {
		final boolean hasConsumedParam = StringUtils.isNotBlank(consumed);
		final Boolean consumedFlag = hasConsumedParam ? Boolean.valueOf(YES.equalsIgnoreCase(consumed)) : null;
		logger.debug("View request: consumerGroup={}, messageCount={}, consumed={}", consumerGroup, limit,
				hasConsumedParam ? consumed : NA);

		// Fast-path: only consumed => DB only
		if (Boolean.TRUE.equals(consumedFlag)) {
			Query query = new Query().addCriteria(Criteria.where(CONSUMED).is(true))
					.with(Sort.by(Sort.Direction.ASC, CREATED_AT)).limit(limit);
			List<Message> fromDb = mongoTemplate.find(query, Message.class, consumerGroup);
			fromDb.sort(Comparator.comparing(Message::getCreatedAt));
			logger.info("Returning {} consumed messages from DB for Consumer Group: {}", fromDb.size(), consumerGroup);
			return fromDb;
		}

		// Cache-first for unconsumed or no filter
		List<Message> cached = cacheService.viewMessages(consumerGroup, limit);
		logger.debug("Cache returned {} {} messages for consumerGroup={}", cached.size(),
				consumedFlag == null ? "(no consumed filter)" : "unconsumed", consumerGroup);

		if (cached.size() >= limit) {
			List<Message> result = new ArrayList<>(cached);
			result.sort(Comparator.comparing(Message::getCreatedAt));
			List<Message> limited = result.subList(0, Math.min(limit, result.size()));
			logger.info("Returning {} {} messages for Consumer Group: {}", limited.size(),
					consumedFlag == null ? "(no consumed filter)" : "unconsumed", consumerGroup);
			return limited;
		}

		Set<String> cachedIds = cached.stream().map(Message::getId).collect(Collectors.toSet());
		int remaining = limit - cached.size();

		Query query = new Query().with(Sort.by(Sort.Direction.ASC, CREATED_AT)).limit(remaining);
		if (Boolean.FALSE.equals(consumedFlag)) {
			query.addCriteria(Criteria.where(CONSUMED).is(false));
		}
		if (!cachedIds.isEmpty()) {
			query.addCriteria(Criteria.where(ID).nin(cachedIds));
			logger.debug("Excluding {} cached IDs from MongoDB query{}", cachedIds.size(),
					consumedFlag == null ? " (no consumed filter)" : "");
		}

		List<Message> fromDb = mongoTemplate.find(query, Message.class, consumerGroup);

		List<Message> combined = new ArrayList<>(cached.size() + fromDb.size());
		combined.addAll(cached);
		combined.addAll(fromDb);
		combined.sort(Comparator.comparing(Message::getCreatedAt));

		if (consumedFlag == null) {
			logger.debug("MongoDB returned {} additional messages (no consumed filter) for consumerGroup={}",
					fromDb.size(), consumerGroup);
			logger.info("Returning {} messages (no consumed filter) for Consumer Group: {}", combined.size(),
					consumerGroup);
		} else {
			logger.debug("MongoDB returned {} additional unconsumed messages for consumerGroup={}", fromDb.size(),
					consumerGroup);
			logger.info("Returning {} unconsumed messages for Consumer Group: {}", combined.size(), consumerGroup);
		}
		return combined;
	}
}
