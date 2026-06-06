package com.example.urlshortener.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShortUrlCacheService {
    private static final Logger log = LoggerFactory.getLogger(ShortUrlCacheService.class);
    private static final String KEY_PREFIX = "url:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration defaultTtl;

    public ShortUrlCacheService(
        StringRedisTemplate redisTemplate,
        Clock clock,
        @Value("${app.cache.url-ttl:PT24H}") Duration defaultTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.defaultTtl = defaultTtl;
    }

    public Optional<String> getLongUrl(String shortCode) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(shortCode)));
        } catch (RuntimeException exception) {
            log.warn("Redis read failed for shortCode={}. Falling back to database.", shortCode, exception);
            return Optional.empty();
        }
    }

    public void putLongUrl(String shortCode, String longUrl, Instant expiresAt) {
        Duration ttl = calculateTtl(expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            evict(shortCode);
            return;
        }

        try {
            redisTemplate.opsForValue().set(key(shortCode), longUrl, ttl);
        } catch (RuntimeException exception) {
            log.warn("Redis write failed for shortCode={}. Continuing without cache.", shortCode, exception);
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
        } catch (RuntimeException exception) {
            log.warn("Redis eviction failed for shortCode={}", shortCode, exception);
        }
    }

    private Duration calculateTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return defaultTtl;
        }

        Duration timeToExpiry = Duration.between(Instant.now(clock), expiresAt);
        if (timeToExpiry.compareTo(defaultTtl) < 0) {
            return timeToExpiry;
        }
        return defaultTtl;
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
