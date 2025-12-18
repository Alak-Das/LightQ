package com.al.lightq.service;

import com.al.lightq.config.LightQProperties;
import com.al.lightq.model.Message;
import com.al.lightq.util.LightQConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CacheService {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final LightQProperties lightQProperties;

    public CacheService(RedisTemplate<String, Object> redisTemplate, LightQProperties lightQProperties) {
        this.redisTemplate = redisTemplate;
        this.lightQProperties = lightQProperties;
    }

    public void addMessage(Message message) {
        String key = LightQConstants.CACHE_PREFIX + message.getConsumerGroup();
        long redisCacheTtlMinutes = lightQProperties.getCacheTtlMinutes();
        logger.debug("Cache add: key={}, messageId={}, ttlMinutes={}", key, message.getId(), redisCacheTtlMinutes);
        redisTemplate.opsForList().leftPush(key, message);
        redisTemplate.expire(key, Duration.ofMinutes(redisCacheTtlMinutes));
    }

    public Message popMessage(String consumerGroup) {
        String key = LightQConstants.CACHE_PREFIX + consumerGroup;
        Message popped = (Message) redisTemplate.opsForList().rightPop(key);
        if (popped != null) {
            logger.debug("Cache hit (pop): key={}, messageId={}", key, popped.getId());
        } else {
            logger.debug("Cache miss (pop): key={}", key);
        }
        return popped;
    }

    public List<Message> viewMessages(String consumerGroup) {
        String key = LightQConstants.CACHE_PREFIX + consumerGroup;
        List<Object> cachedObjects = redisTemplate.opsForList().range(key, 0, -1);
        if (cachedObjects == null || cachedObjects.isEmpty()) {
            logger.debug("Cache view: no entries for key={}", key);
            return Collections.emptyList();
        }
        List<Message> messages = cachedObjects.stream()
                .filter(obj -> obj instanceof Message)
                .map(obj -> (Message) obj)
                .collect(Collectors.toList());
        logger.debug("Cache view: key={}, size={}", key, messages.size());
        return messages;
    }
}
