package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

import java.time.Duration;
import java.time.Instant;

/**
 * Przykład modelu pod rate limiting.
 *
 * Key:
 * rate-limit:{userId}:{window}
 *
 * Value:
 * licznik requestów w danym oknie czasowym.
 */
public class RateLimitEntry {

    private final String userId;
    private final String window;
    private final int requestCount;
    private final int maxRequests;
    private final Instant windowStartedAt;
    private final Duration ttl;

    public RateLimitEntry(
            String userId,
            String window,
            int requestCount,
            int maxRequests,
            Instant windowStartedAt,
            Duration ttl
    ) {
        this.userId = userId;
        this.window = window;
        this.requestCount = requestCount;
        this.maxRequests = maxRequests;
        this.windowStartedAt = windowStartedAt;
        this.ttl = ttl;
    }

    public static String key(String userId, String window) {
        return "rate-limit:" + userId + ":" + window;
    }

    public boolean isLimitExceeded() {
        return requestCount >= maxRequests;
    }

    public RateLimitEntry increment() {
        return new RateLimitEntry(userId, window, requestCount + 1, maxRequests, windowStartedAt, ttl);
    }

    public String userId() { return userId; }
    public String window() { return window; }
    public int requestCount() { return requestCount; }
    public int maxRequests() { return maxRequests; }
    public Instant windowStartedAt() { return windowStartedAt; }
    public Duration ttl() { return ttl; }
}
