package pl.jakubtworek.backend.gateway.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class RedisTokenBucketRateLimiter {
    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refillTokens = tonumber(ARGV[2])
            local refillPeriodMs = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local nowMs = tonumber(ARGV[5])
            local ttlSeconds = tonumber(ARGV[6])

            local bucket = redis.call('HMGET', key, 'tokens', 'updatedAt')
            local tokens = tonumber(bucket[1])
            local updatedAt = tonumber(bucket[2])

            if tokens == nil then
              tokens = capacity
              updatedAt = nowMs
            end

            local elapsed = math.max(0, nowMs - updatedAt)
            local refill = math.floor(elapsed / refillPeriodMs) * refillTokens
            tokens = math.min(capacity, tokens + refill)

            if refill > 0 then
              updatedAt = nowMs
            end

            local allowed = 0
            if tokens >= requested then
              allowed = 1
              tokens = tokens - requested
            end

            local retryAfterMs = 0
            if allowed == 0 then
              local missing = requested - tokens
              local periods = math.ceil(missing / refillTokens)
              retryAfterMs = periods * refillPeriodMs
            end

            redis.call('HMSET', key, 'tokens', tokens, 'updatedAt', updatedAt)
            redis.call('EXPIRE', key, ttlSeconds)
            return { allowed, tokens, math.ceil(retryAfterMs / 1000) }
            """;

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final DefaultRedisScript<List> script;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.clock = Clock.systemUTC();
        this.script = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    public TokenBucketDecision consume(String key, RateLimitProperties.Bucket bucket) {
        long refillMs = Math.max(1, bucket.refillPeriod().toMillis());
        long ttlSeconds = Math.max(60, bucket.refillPeriod().toSeconds() * 3);
        List<?> result = redisTemplate.execute(
                script,
                List.of(key),
                String.valueOf(bucket.capacity()),
                String.valueOf(bucket.refillTokens()),
                String.valueOf(refillMs),
                "1",
                String.valueOf(clock.millis()),
                String.valueOf(ttlSeconds)
        );
        if (result == null || result.size() < 3) {
            return new TokenBucketDecision(true, -1, 0);
        }
        long allowed = asLong(result.get(0));
        long remaining = asLong(result.get(1));
        long retryAfter = asLong(result.get(2));
        return new TokenBucketDecision(allowed == 1, remaining, retryAfter);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
