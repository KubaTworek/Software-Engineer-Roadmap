package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;

/**
 * Defines resilience settings for one remote call.
 *
 * Every remote call should have explicit timeouts.
 * Retries should be limited, jittered, and used only when the operation is retry-safe.
 */
public record RemoteCallPolicy(
        String name,
        RemoteCallType callType,
        Duration connectionTimeout,
        Duration requestTimeout,
        int maxRetryAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        boolean jitterEnabled,
        boolean circuitBreakerEnabled,
        IdempotencyRequirement idempotencyRequirement
) {
    public RemoteCallPolicy {
        requireText(name, "name");
        if (callType == null) {
            throw new IllegalArgumentException("callType is required");
        }
        requirePositiveDuration(connectionTimeout, "connectionTimeout");
        requirePositiveDuration(requestTimeout, "requestTimeout");
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be non-negative");
        }
        requirePositiveDuration(retryBaseDelay, "retryBaseDelay");
        requirePositiveDuration(retryMaxDelay, "retryMaxDelay");
        if (retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new IllegalArgumentException("retryMaxDelay must be greater than or equal to retryBaseDelay");
        }
        if (idempotencyRequirement == null) {
            throw new IllegalArgumentException("idempotencyRequirement is required");
        }
    }

    /**
     * Retry is unsafe when the operation is not idempotent and has no idempotency key.
     */
    public boolean hasUnsafeRetryConfiguration() {
        return maxRetryAttempts > 0 && idempotencyRequirement == IdempotencyRequirement.NOT_RETRY_SAFE;
    }

    /**
     * Retries without jitter can synchronize clients and amplify an outage.
     */
    public boolean canSynchronizeRetries() {
        return maxRetryAttempts > 0 && !jitterEnabled;
    }

    /**
     * Critical external calls usually need a circuit breaker.
     */
    public boolean lacksCircuitBreakerForCriticalExternalCall() {
        return (callType == RemoteCallType.PAYMENT_API || callType == RemoteCallType.THIRD_PARTY_API)
                && !circuitBreakerEnabled;
    }

    private static void requirePositiveDuration(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
