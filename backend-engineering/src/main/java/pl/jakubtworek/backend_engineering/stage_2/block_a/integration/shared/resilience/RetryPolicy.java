package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.resilience;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Minimal synchronous retry loop used to explain error classification.
 *
 * <p>The caller must provide the retryability rule. Retrying every runtime
 * exception would repeat validation and programming errors and could duplicate
 * non-idempotent effects. Production network code additionally needs a timeout,
 * bounded backoff, jitter, observability and a total retry budget.</p>
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Predicate<RuntimeException> retryable;

    public RetryPolicy(int maxAttempts, Predicate<RuntimeException> retryable) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Max attempts must be positive");
        }

        this.maxAttempts = maxAttempts;
        this.retryable = Objects.requireNonNull(retryable, "retryable must not be null");
    }

    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException exception) {
                if (!retryable.test(exception) || attempt == maxAttempts) {
                    throw exception;
                }
                lastError = exception;
            }
        }

        // The loop either returns or throws. This guard protects the invariant
        // if the implementation is changed without preserving that property.
        throw lastError;
    }
}
