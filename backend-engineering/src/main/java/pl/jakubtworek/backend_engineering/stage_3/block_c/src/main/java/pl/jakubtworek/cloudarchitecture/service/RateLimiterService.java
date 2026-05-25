package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * Redis-based fixed-window rate limiter.
 *
 * Rate limiting protects the backend from accidental client loops, abuse,
 * and unexpected traffic spikes that could increase cost or overload databases.
 */
@Service
public class RateLimiterService {
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Increments a per-user counter and rejects requests above the limit. */
    public void checkLimit(String userId) {
        String key = "rate-limit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            throw new RateLimitExceededException("Rate limit exceeded for user: " + userId);
        }
    }
}
