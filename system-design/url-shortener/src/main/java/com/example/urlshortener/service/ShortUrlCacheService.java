package com.example.urlshortener.service;

import com.example.urlshortener.storage.UrlLookupRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis projection used by the hot redirect path.
 *
 * <p>The value deliberately contains status, expiration and entity version. A
 * cache containing only the destination cannot distinguish an active link from
 * a revoked one. PostgreSQL remains the source of truth; cache failures degrade
 * latency, while projection updates are performed only after source commit.</p>
 */
@Service
public class ShortUrlCacheService {
    private static final Logger log = LoggerFactory.getLogger(ShortUrlCacheService.class);
    private static final String KEY_PREFIX = "url:";
    private static final DefaultRedisScript<Long> VERSIONED_PUT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current then
                local decoded = cjson.decode(current)
                if decoded.version and tonumber(decoded.version) > tonumber(ARGV[2]) then
                    return 0
                end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration defaultTtl;

    public ShortUrlCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.cache.url-ttl:PT24H}") Duration defaultTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.defaultTtl = defaultTtl;
    }

    public Optional<UrlLookupRecord> get(String shortCode) {
        try {
            String value = redisTemplate.opsForValue().get(key(shortCode));
            return value == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(value, UrlLookupRecord.class));
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Redis lookup failed for shortCode={}; using source of truth", shortCode, exception);
            return Optional.empty();
        }
    }

    public boolean put(UrlLookupRecord record) {
        Duration ttl = calculateTtl(record);
        try {
            String payload = objectMapper.writeValueAsString(record);
            Long result = redisTemplate.execute(
                    VERSIONED_PUT,
                    List.of(key(record.shortCode())),
                    payload,
                    Long.toString(record.version()),
                    Long.toString(Math.max(1, ttl.toMillis()))
            );
            // 0 means that Redis already contains a newer source version. Ignoring
            // the stale delivery is a successful, idempotent projection outcome.
            return result != null;
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Redis projection update failed for shortCode={}", record.shortCode(), exception);
            return false;
        }
    }

    public boolean evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
            return true;
        } catch (RuntimeException exception) {
            // Revocation has a measurable propagation SLO, not a zero-staleness guarantee.
            log.warn("Redis projection eviction failed for shortCode={}", shortCode, exception);
            return false;
        }
    }

    private Duration calculateTtl(UrlLookupRecord record) {
        Instant expiresAt = record.expiresAt();
        if (expiresAt == null) {
            return defaultTtl;
        }
        Duration remaining = Duration.between(Instant.now(clock), expiresAt);
        // An expired, blocked or deleted record is negative cache state. Keeping
        // it for a bounded TTL prevents repeated primary reads and, more
        // importantly, preserves the tombstone against delayed ACTIVE events.
        if (remaining.isZero() || remaining.isNegative()) {
            return defaultTtl;
        }
        return remaining.compareTo(defaultTtl) < 0 ? remaining : defaultTtl;
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
