package pl.jakubtworek.backend.gateway.ratelimit;

public record TokenBucketDecision(boolean allowed, long remainingTokens, long retryAfterSeconds) {
}
