package pl.jakubtworek.booking.cache;

import java.time.Instant;

public final class RateLimitBucket {
    private final int maxTokens;
    private int remainingTokens;
    private Instant resetAt;

    public RateLimitBucket(int maxTokens, Instant resetAt) {
        this.maxTokens = maxTokens;
        this.remainingTokens = maxTokens;
        this.resetAt = resetAt;
    }

    public synchronized RateLimitDecision consume(Instant now, java.time.Duration window) {
        if (!resetAt.isAfter(now)) {
            remainingTokens = maxTokens;
            resetAt = now.plus(window);
        }
        if (remainingTokens <= 0) {
            return new RateLimitDecision(false, 0, resetAt);
        }
        remainingTokens--;
        return new RateLimitDecision(true, remainingTokens, resetAt);
    }
}
