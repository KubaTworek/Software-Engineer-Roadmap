package com.example.newsfeed.feed;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FeedCacheService {

    private static final Duration FEED_CACHE_TTL = Duration.ofSeconds(30);
    private static final String GLOBAL_FEED_PREFIX = "feed:global:";
    private static final String PERSONALIZED_FEED_PREFIX = "feed:user:";

    private final RedisTemplate<String, FeedResponse> redisTemplate;

    public FeedCacheService(RedisTemplate<String, FeedResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "emptyGlobal")
    public Optional<FeedResponse> getGlobalFeed(String cacheKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(GLOBAL_FEED_PREFIX + cacheKey));
    }

    @CircuitBreaker(name = "redis")
    public void putGlobalFeed(String cacheKey, FeedResponse response) {
        redisTemplate.opsForValue().set(GLOBAL_FEED_PREFIX + cacheKey, response, FEED_CACHE_TTL);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "emptyPersonalized")
    public Optional<FeedResponse> getPersonalizedFeed(UUID userId, String cacheKey) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(PERSONALIZED_FEED_PREFIX + userId + ":" + cacheKey));
    }

    @CircuitBreaker(name = "redis")
    public void putPersonalizedFeed(UUID userId, String cacheKey, FeedResponse response) {
        redisTemplate.opsForValue().set(PERSONALIZED_FEED_PREFIX + userId + ":" + cacheKey, response, FEED_CACHE_TTL);
    }

    public void evictGlobalFeed() {
        deleteByPattern(GLOBAL_FEED_PREFIX + "*");
    }

    public void evictPersonalizedFeed(UUID userId) {
        deleteByPattern(PERSONALIZED_FEED_PREFIX + userId + ":*");
    }

    private void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Optional<FeedResponse> emptyGlobal(String cacheKey, Throwable throwable) {
        return Optional.empty();
    }

    private Optional<FeedResponse> emptyPersonalized(UUID userId, String cacheKey, Throwable throwable) {
        return Optional.empty();
    }
}
