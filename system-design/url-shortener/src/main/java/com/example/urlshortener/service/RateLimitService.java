package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Distributed fixed-window limiter.
 *
 * <p>The invariant is not merely an atomic increment. The first increment and
 * assignment of the window TTL must be indivisible; otherwise a process crash
 * can leave a permanent counter. Lua gives that boundary on one Redis primary.</p>
 *
 * <p>Redis failure is deliberately fail-open. This protects redirect availability
 * but weakens abuse protection, so callers with stricter security requirements
 * should use a separate fail-closed policy.</p>
 */
@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final DefaultRedisScript<Long> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkFixedWindow(String key, long limit, Duration window) {
        if (limit <= 0) return;
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate-limit window must be positive");
        }

        try {
            Long current = redis.execute(FIXED_WINDOW_SCRIPT, List.of(key), Long.toString(window.toMillis()));
            if (current != null && current > limit) {
                Long ttl = redis.getExpire(key);
                long retryAfter = ttl == null || ttl < 0 ? Math.max(1, window.toSeconds()) : Math.max(1, ttl);
                throw new RateLimitExceededException("Too many requests. Try again later.", retryAfter);
            }
        } catch (RateLimitExceededException limitExceeded) {
            throw limitExceeded;
        } catch (RuntimeException redisFailure) {
            log.warn("Redis rate-limit check failed for key={}; failing open", key, redisFailure);
        }
    }
}
