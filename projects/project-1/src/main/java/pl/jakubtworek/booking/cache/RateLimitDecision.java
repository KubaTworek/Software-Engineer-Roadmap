package pl.jakubtworek.booking.cache;

import java.time.Instant;

public record RateLimitDecision(boolean allowed, int remainingTokens, Instant resetAt) {
}
