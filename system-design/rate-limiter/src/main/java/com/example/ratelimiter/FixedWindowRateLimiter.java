package com.example.ratelimiter;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage-1 implementation: in-memory Fixed Window Counter.
 *
 * This is intentionally simple:
 * - one counter per client key,
 * - no Redis,
 * - no distributed synchronization,
 * - safe enough for a single running application instance.
 */
public final class FixedWindowRateLimiter implements RateLimiter {
    private final RateLimitConfig config;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimitConfig config) {
        this(config, Clock.systemUTC());
    }

    public FixedWindowRateLimiter(RateLimitConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision check(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = "anonymous";
        }

        long now = clock.millis();
        long windowMillis = config.window().toMillis();
        long currentWindowStart = now - (now % windowMillis);
        long resetAt = currentWindowStart + windowMillis;

        final String normalizedClientKey = clientKey;

        WindowCounter result = counters.compute(normalizedClientKey, (key, existingCounter) -> {
            if (existingCounter == null || existingCounter.windowStartMillis != currentWindowStart) {
                return new WindowCounter(currentWindowStart, 1);
            }

            if (existingCounter.count < config.maxRequests()) {
                return new WindowCounter(existingCounter.windowStartMillis, existingCounter.count + 1);
            }

            return existingCounter;
        });

        boolean allowed = result.count <= config.maxRequests();
        int remaining = Math.max(config.maxRequests() - result.count, 0);
        long retryAfterMillis = allowed ? 0 : resetAt - now;

        cleanupExpiredCounters(currentWindowStart);

        return new RateLimitDecision(
                allowed,
                config.maxRequests(),
                remaining,
                resetAt,
                retryAfterMillis
        );
    }

    /**
     * Basic cleanup for old client windows.
     * This is enough for stage 1. In a production implementation this would be more deliberate,
     * for example scheduled cleanup or Redis TTL.
     */
    private void cleanupExpiredCounters(long currentWindowStart) {
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartMillis < currentWindowStart);
    }

    private static final class WindowCounter {
        private final long windowStartMillis;
        private final int count;

        private WindowCounter(long windowStartMillis, int count) {
            this.windowStartMillis = windowStartMillis;
            this.count = count;
        }
    }
}
