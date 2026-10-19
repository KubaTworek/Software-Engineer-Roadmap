package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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
        this.userId = requireNonBlank(userId, "userId");
        this.window = requireNonBlank(window, "window");
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount must not be negative");
        }
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be greater than zero");
        }
        this.requestCount = requestCount;
        this.maxRequests = maxRequests;
        this.windowStartedAt = Objects.requireNonNull(windowStartedAt, "windowStartedAt must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }
    }

    public static String key(String userId, String window) {
        return "rate-limit:" + userId + ":" + window;
    }

    public boolean isLimitExceeded() {
        return requestCount >= maxRequests;
    }

    public RateLimitEntry increment() {
        // This value object does not make a distributed read-modify-write atomic.
        // A real key-value store must increment and set/retain TTL atomically, for
        // example with Redis INCR + EXPIRE in Lua or an equivalent server-side operation.
        return new RateLimitEntry(
                userId,
                window,
                Math.addExact(requestCount, 1),
                maxRequests,
                windowStartedAt,
                ttl
        );
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String userId() { return userId; }
    public String window() { return window; }
    public int requestCount() { return requestCount; }
    public int maxRequests() { return maxRequests; }
    public Instant windowStartedAt() { return windowStartedAt; }
    public Duration ttl() { return ttl; }
}
