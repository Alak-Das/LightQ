package com.al.lightq.service;

import static com.al.lightq.LightQConstants.*;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Service for Dead-Letter Queue operations: view and replay.
 */
@Service
public class DlqService {

	private static final Logger logger = LoggerFactory.getLogger(DlqService.class);

	private final MongoTemplate mongoTemplate;
	private final CacheService cacheService;
	private final LightQProperties lightQProperties;

	public DlqService(MongoTemplate mongoTemplate, CacheService cacheService, LightQProperties lightQProperties) {
		this.mongoTemplate = mongoTemplate;
		this.cacheService = cacheService;
		this.lightQProperties = lightQProperties;
	}

	/**
	 * View latest DLQ entries for the consumer group.
	 */
	public List<Document> view(String consumerGroup, int limit) {
		String dlqCollection = consumerGroup + lightQProperties.getDlqSuffix();
		Query q = new Query().with(Sort.by(Sort.Direction.DESC, FAILED_AT)).limit(limit);
		List<Document> docs = mongoTemplate.find(q, Document.class, dlqCollection);
		int size = docs != null ? docs.size() : 0;
		logger.debug("DLQ view: group={}, limit={}, returned={}", consumerGroup, limit, size);
		return docs != null ? docs : List.of();
	}

	/**
	 * Replay DLQ entries by IDs. For each DLQ entry: - Creates a new Message with a
	 * new ID and the same content/consumerGroup. - Saves to main collection with
	 * consumed=false. - Pushes to cache for immediate availability. - Deletes the
	 * DLQ entry. Returns the number of successfully replayed entries.
	 */
	public int replay(String consumerGroup, List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			logger.debug("DLQ replay requested with empty ids for group={}", consumerGroup);
			return 0;
		}
		String dlqCollection = consumerGroup + lightQProperties.getDlqSuffix();
		int count = 0;
		for (String id : ids) {
			Document doc = mongoTemplate.findById(id, Document.class, dlqCollection);
			if (doc == null) {
				logger.debug("DLQ replay: id={} not found in {}", id, dlqCollection);
				continue;
			}
			String content = doc.getString(CONTENT);
			if (content == null) {
				logger.warn("DLQ replay: id={} missing content, skipping", id);
				continue;
			}
			// Create new message with a fresh id
			String newId = UUID.randomUUID().toString();
			Message message = new Message(newId, consumerGroup, content);
			// Ensure fields for correctness
			// createdAt initialized in Message(messageId, consumerGroup, content) ctor
			// consumed defaults false

			// Save to main collection then push to cache
			mongoTemplate.save(message, consumerGroup);
			cacheService.addMessage(message);

			// Remove DLQ entry
			mongoTemplate.remove(new Query(Criteria.where(ID).is(id)), dlqCollection);

			count++;
			logger.info("DLQ replayed id={} into new messageId={} for group={}", id, newId, consumerGroup);
		}
		logger.info("DLQ replay completed: group={}, requested={}, replayed={}", consumerGroup, ids.size(), count);
		return count;
	}
}
