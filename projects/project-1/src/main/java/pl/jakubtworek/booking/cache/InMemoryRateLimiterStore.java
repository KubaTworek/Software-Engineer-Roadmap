package pl.jakubtworek.booking.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!nosql-real")
public class InMemoryRateLimiterStore implements RateLimiterStore {
    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision consume(String clientKey, int maxTokens, Duration window) {
        Instant now = Instant.now();
        RateLimitBucket bucket = buckets.computeIfAbsent(clientKey, ignored -> new RateLimitBucket(maxTokens, now.plus(window)));
        return bucket.consume(now, window);
    }
}
