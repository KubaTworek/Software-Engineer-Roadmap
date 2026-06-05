package com.example.ratelimiter;

/**
 * Result returned by the limiter for a single request.
 */
public final class RateLimitDecision {
    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetEpochMillis;
    private final long retryAfterMillis;

    public RateLimitDecision(
            boolean allowed,
            int limit,
            int remaining,
            long resetEpochMillis,
            long retryAfterMillis
    ) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = Math.max(remaining, 0);
        this.resetEpochMillis = resetEpochMillis;
        this.retryAfterMillis = Math.max(retryAfterMillis, 0);
    }

    public boolean allowed() {
        return allowed;
    }

    public int limit() {
        return limit;
    }

    public int remaining() {
        return remaining;
    }

    public long resetEpochMillis() {
        return resetEpochMillis;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
