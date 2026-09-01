package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class
    );
    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /** Atomically increments a per-user counter, assigns its TTL, and enforces the limit. */
    public void checkLimit(String userId) {
        String validatedUserId = requireNonBlank(userId, "userId");
        Long count = redisTemplate.execute(
                INCREMENT_WITH_EXPIRY,
                List.of("rate-limit:" + validatedUserId),
                String.valueOf(WINDOW.toSeconds())
        );
        if (count == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter");
        }
        if (count > MAX_REQUESTS_PER_MINUTE) {
            throw new RateLimitExceededException("Rate limit exceeded for user: " + userId);
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
