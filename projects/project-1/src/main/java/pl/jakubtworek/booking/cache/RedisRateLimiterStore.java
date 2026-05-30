package pl.jakubtworek.booking.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Profile("nosql-real")
public class RedisRateLimiterStore implements RateLimiterStore {
    private static final String PREFIX = "rate-limit:";
    private final StringRedisTemplate redis;

    public RedisRateLimiterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision consume(String clientKey, int maxTokens, Duration window) {
        String key = PREFIX + clientKey;
        redis.opsForValue().setIfAbsent(key, String.valueOf(maxTokens), window);
        Long value = redis.opsForValue().decrement(key);
        if (value == null || value < 0) {
            if (value != null && value < 0) {
                redis.opsForValue().increment(key);
            }
            Instant resetAt = Instant.now().plus(window);
            return new RateLimitDecision(false, 0, resetAt);
        }
        return new RateLimitDecision(true, value.intValue(), Instant.now().plus(window));
    }
}
