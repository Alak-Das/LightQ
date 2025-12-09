package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import com.al.simplequeueservice.util.SQSConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.ttl.minutes}")
    private long redisCacheTtlMinutes;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addMessage(Message message) {
        String key = SQSConstants.CACHE_PREFIX + message.getConsumerGroup();
        redisTemplate.opsForList().leftPush(key, message);
        redisTemplate.expire(key, Duration.ofMinutes(redisCacheTtlMinutes));
    }

    public Message popMessage(String consumerGroup) {
        String key = SQSConstants.CACHE_PREFIX + consumerGroup;
        return (Message) redisTemplate.opsForList().rightPop(key);
    }

    public List<Message> viewMessages(String consumerGroup) {
        String key = SQSConstants.CACHE_PREFIX + consumerGroup;
        List<Object> cachedObjects = redisTemplate.opsForList().range(key, 0, -1);
        if (cachedObjects == null || cachedObjects.isEmpty()) {
            return Collections.emptyList();
        }
        return cachedObjects.stream()
                .filter(obj -> obj instanceof Message)
                .map(obj -> (Message) obj)
                .collect(Collectors.toList());
    }
}
