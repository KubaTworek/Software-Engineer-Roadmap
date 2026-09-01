package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Simple circuit breaker.
 *
 * CLOSED: calls are allowed.
 * OPEN: calls fail fast.
 * HALF_OPEN: one trial call checks whether dependency recovered.
 *
 * Only failures classified as dependency failures should affect the breaker.
 * Validation and programming errors belong to the caller and must not make a
 * healthy dependency look unavailable.
 */
public class CircuitBreaker {

    private final String dependencyName;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final Predicate<Exception> countsAsDependencyFailure;

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt = Instant.EPOCH;
    private boolean halfOpenTrialInProgress;

    public CircuitBreaker(
            String dependencyName,
            int failureThreshold,
            Duration openDuration
    ) {
        this(dependencyName, failureThreshold, openDuration, Clock.systemUTC(), ignored -> true);
    }

    public CircuitBreaker(
            String dependencyName,
            int failureThreshold,
            Duration openDuration,
            Clock clock
    ) {
        this(dependencyName, failureThreshold, openDuration, clock, ignored -> true);
    }

    public CircuitBreaker(
            String dependencyName,
            int failureThreshold,
            Duration openDuration,
            Clock clock,
            Predicate<Exception> countsAsDependencyFailure
    ) {
        if (dependencyName == null || dependencyName.isBlank()) throw new IllegalArgumentException("dependencyName is required");
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be positive");
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) throw new IllegalArgumentException("openDuration must be positive");
        if (clock == null) throw new IllegalArgumentException("clock is required");

        this.dependencyName = dependencyName;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
        this.countsAsDependencyFailure = Objects.requireNonNull(
                countsAsDependencyFailure,
                "countsAsDependencyFailure must not be null"
        );
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation, "operation must not be null");
        beforeCall();

        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception exception) {
            if (countsAsDependencyFailure.test(exception)) {
                onFailure();
            } else {
                onIgnoredFailure();
            }
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

        if (state == CircuitBreakerState.HALF_OPEN) {
            if (halfOpenTrialInProgress) {
                throw new CircuitBreakerOpenException(dependencyName);
            }
            halfOpenTrialInProgress = true;
        }
    }

    private synchronized void onSuccess() {
        state = CircuitBreakerState.CLOSED;
        consecutiveFailures = 0;
        halfOpenTrialInProgress = false;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;
        halfOpenTrialInProgress = false;

        if (state == CircuitBreakerState.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = CircuitBreakerState.OPEN;
            openedAt = clock.instant();
        }
    }

    private synchronized void onIgnoredFailure() {
        halfOpenTrialInProgress = false;
    }

    private void moveToHalfOpenIfReady() {
        if (state == CircuitBreakerState.OPEN
                && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = CircuitBreakerState.HALF_OPEN;
            halfOpenTrialInProgress = false;
        }
    }
}
