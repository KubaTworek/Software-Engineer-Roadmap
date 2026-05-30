package pl.jakubtworek.booking.cache;

import java.time.Duration;

public interface RateLimiterStore {
    RateLimitDecision consume(String clientKey, int maxTokens, Duration window);
}
