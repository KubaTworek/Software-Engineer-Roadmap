package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.key_value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Wykonywalny model atomowej operacji „increment + ustaw TTL przy utworzeniu”.
 * Synchronizacja zastępuje tu skrypt Redis/Lua lub inną operację serwerową.
 */
public final class AtomicFixedWindowRateLimiter {

    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public AtomicFixedWindowRateLimiter(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized RateLimitDecision tryAcquire(String key, int limit, Duration windowSize) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
            throw new IllegalArgumentException("windowSize must be positive");
        }

        Instant now = clock.instant();
        Window current = windows.get(key);
        if (current == null || !now.isBefore(current.expiresAt())) {
            current = new Window(0, now.plus(windowSize));
        }

        boolean allowed = current.used() < limit;
        Window updated = allowed ? new Window(current.used() + 1, current.expiresAt()) : current;
        windows.put(key, updated);

        int remaining = Math.max(0, limit - updated.used());
        Duration retryAfter = allowed ? Duration.ZERO : Duration.between(now, updated.expiresAt());
        return new RateLimitDecision(allowed, remaining, updated.expiresAt(), retryAfter);
    }

    private record Window(int used, Instant expiresAt) {
    }

    public record RateLimitDecision(
            boolean allowed,
            int remaining,
            Instant resetAt,
            Duration retryAfter
    ) {
    }
}
