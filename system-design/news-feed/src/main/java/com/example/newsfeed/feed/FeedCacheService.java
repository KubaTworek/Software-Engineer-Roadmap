package com.example.newsfeed.feed;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.*;

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
    private final Counter hits;
    private final Counter misses;

    public FeedCacheService(RedisTemplate<String, FeedResponse> redisTemplate, MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.hits = registry.counter("newsfeed.feed.cache.requests", "result", "hit");
        this.misses = registry.counter("newsfeed.feed.cache.requests", "result", "miss");
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "emptyGlobal")
    public Optional<FeedResponse> getGlobalFeed(String cacheKey) {
        return measured(redisTemplate.opsForValue().get(GLOBAL_FEED_PREFIX + cacheKey));
    }

    @CircuitBreaker(name = "redis")
    public void putGlobalFeed(String cacheKey, FeedResponse response) {
        redisTemplate.opsForValue().set(GLOBAL_FEED_PREFIX + cacheKey, response, FEED_CACHE_TTL);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "emptyPersonalized")
    public Optional<FeedResponse> getPersonalizedFeed(UUID userId, String cacheKey) {
        return measured(redisTemplate.opsForValue().get(PERSONALIZED_FEED_PREFIX + userId + ":" + cacheKey));
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
        /* KEYS blokuje event loop Redisa proporcjonalnie do całej bazy. */
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            var options = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(pattern).count(200).build();
            try (var cursor = connection.keyCommands().scan(options)) {
                java.util.List<byte[]> batch = new java.util.ArrayList<>();
                cursor.forEachRemaining(batch::add);
                if (!batch.isEmpty()) connection.keyCommands().del(batch.toArray(byte[][]::new));
            }
            return null;
        });
    }

    private Optional<FeedResponse> emptyGlobal(String cacheKey, Throwable throwable) {
        return Optional.empty();
    }

    private Optional<FeedResponse> emptyPersonalized(UUID userId, String cacheKey, Throwable throwable) {
        return Optional.empty();
    }

    private Optional<FeedResponse> measured(FeedResponse response) {
        if (response == null) misses.increment(); else hits.increment();
        return Optional.ofNullable(response);
    }
}
