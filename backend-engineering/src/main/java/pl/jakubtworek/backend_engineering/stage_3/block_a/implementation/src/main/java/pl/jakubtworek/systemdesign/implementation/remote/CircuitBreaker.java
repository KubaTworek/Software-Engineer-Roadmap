package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * Simple circuit breaker implementation.
 *
 * CLOSED:
 * Calls pass normally.
 *
 * OPEN:
 * Calls fail fast.
 *
 * HALF_OPEN:
 * A limited trial call is isAllowed to check recovery.
 */
public class CircuitBreaker {

    private final String dependencyName;
    private final int failureThreshold;
    private final Duration openDuration;

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt = Instant.EPOCH;

    public CircuitBreaker(String dependencyName, int failureThreshold, Duration openDuration) {
        if (dependencyName == null || dependencyName.isBlank()) {
            throw new IllegalArgumentException("dependencyName is required");
        }

        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }

        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }

        this.dependencyName = dependencyName;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        beforeCall();

        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception exception) {
            onFailure();
            throw exception;
        }
    }

    public synchronized CircuitBreakerState state() {
        moveToHalfOpenIfReady();
        return state;
    }

    private synchronized void beforeCall() {
        moveToHalfOpenIfReady();

        if (state == CircuitBreakerState.OPEN) {
            throw new CircuitBreakerOpenException(dependencyName);
        }
    }

    private synchronized void onSuccess() {
        state = CircuitBreakerState.CLOSED;
        consecutiveFailures = 0;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;

        if (state == CircuitBreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = CircuitBreakerState.OPEN;
            openedAt = Instant.now();
        }
    }

    private void moveToHalfOpenIfReady() {
        if (state == CircuitBreakerState.OPEN && Instant.now().isAfter(openedAt.plus(openDuration))) {
            state = CircuitBreakerState.HALF_OPEN;
        }
    }
}