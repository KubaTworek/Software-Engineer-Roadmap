package com.example.ratelimiter;

import java.time.Duration;

/**
 * Configuration for the stage-1 rate limiter.
 *
 * Stage 1 intentionally keeps configuration simple and static:
 * - one limit for all clients,
 * - one window size,
 * - in-memory storage.
 */
public final class RateLimitConfig {
    private final int maxRequests;
    private final Duration window;

    public RateLimitConfig(int maxRequests, Duration window) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be greater than 0");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public int maxRequests() {
        return maxRequests;
    }

    public Duration window() {
        return window;
    }
}
