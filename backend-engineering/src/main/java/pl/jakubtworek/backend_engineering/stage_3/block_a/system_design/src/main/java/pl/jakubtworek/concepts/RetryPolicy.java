package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Retry policy with exponential backoff and jitter.
 *
 * Rule of thumb:
 * - retry only transient failures
 * - retry only idempotent operations or operations protected by idempotency keys
 * - use a maximum number of attempts
 * - avoid retrying at many layers at the same time
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Predicate<Exception> retryable;

    public RetryPolicy(
            int maxAttempts,
            Duration baseDelay,
            Duration maxDelay,
            Predicate<Exception> retryable
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) {
            throw new IllegalArgumentException("baseDelay must be positive");
        }
        if (maxDelay == null || maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to baseDelay");
        }

        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.retryable = Objects.requireNonNull(retryable);
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        Objects.requireNonNull(operation);

        int attempt = 0;

        while (true) {
            try {
                return operation.call();
            } catch (Exception error) {
                attempt++;

                if (attempt >= maxAttempts || !retryable.test(error)) {
                    throw error;
                }

                Thread.sleep(delayForAttempt(attempt).toMillis());
            }
        }
    }

    /**
     * delay_k = min(cap, base * 2^k) + jitter
     */
    public Duration delayForAttempt(int attempt) {
        long exponentialMillis = baseDelay.toMillis() * (1L << Math.min(attempt, 30));
        long cappedMillis = Math.min(maxDelay.toMillis(), exponentialMillis);
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseDelay.toMillis()));
        return Duration.ofMillis(cappedMillis + jitterMillis);
    }
}
