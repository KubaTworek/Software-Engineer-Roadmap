package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Simple circuit breaker with three states:
 *
 * CLOSED    - calls are allowed normally
 * OPEN      - calls fail fast
 * HALF_OPEN - a limited trial call is allowed to check if dependency recovered
 *
 * This implementation is intentionally compact. Production systems should use
 * mature libraries and export metrics for state transitions, failure rate,
 * timeout rate, rejected calls, and dependency latency.
 */
public class CircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt = Instant.EPOCH;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public synchronized State state() {
        transitionToHalfOpenIfReady();
        return state;
    }

    /**
     * Executes a protected dependency call.
     *
     * When the breaker is open, the method fails fast instead of making
     * the dependency even less healthy.
     */
    public <T> T execute(Callable<T> dependencyCall) throws Exception {
        Objects.requireNonNull(dependencyCall);

        synchronized (this) {
            transitionToHalfOpenIfReady();
            if (state == State.OPEN) {
                throw new CircuitBreakerOpenException("Circuit breaker is open");
            }
        }

        try {
            T result = dependencyCall.call();
            onSuccess();
            return result;
        } catch (Exception error) {
            onFailure();
            throw error;
        }
    }

    private synchronized void onSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;

        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = Instant.now();
        }
    }

    private void transitionToHalfOpenIfReady() {
        if (state == State.OPEN && Instant.now().isAfter(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
        }
    }
}
