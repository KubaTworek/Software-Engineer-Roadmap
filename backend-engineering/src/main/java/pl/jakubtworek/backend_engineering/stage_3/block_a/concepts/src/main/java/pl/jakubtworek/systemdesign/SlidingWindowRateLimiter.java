package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window rate limiter.
 *
 * Definition:
 * allowed if requests during the last window duration are below the limit.
 *
 * This implementation is simple and educational. High-throughput systems often
 * use approximate counters, segmented windows, Redis scripts, or gateway-native
 * rate limiting instead.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int limit, Duration window) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.limit = limit;
        this.window = window;
    }

    @Override
    public RateLimitDecision allow(String identity) {
        Deque<Instant> timestamps = requests.computeIfAbsent(identity, ignored -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }

            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return RateLimitDecision.allowed();
            }

            Instant oldest = timestamps.peekFirst();
            Duration retryAfter = Duration.between(now, oldest.plus(window));
            return RateLimitDecision.rejected(retryAfter);
        }
    }
}
