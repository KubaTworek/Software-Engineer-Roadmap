package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes retry with exponential backoff and jitter.
 *
 * Use only for transient failures and idempotent operations,
 * or operations protected by idempotency keys.
 */
public class RetryExecutor {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final RetryClassifier classifier;

    public RetryExecutor(
            int maxAttempts,
            Duration baseDelay,
            Duration maxDelay,
            RetryClassifier classifier
    ) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        if (baseDelay == null || baseDelay.isNegative() || baseDelay.isZero()) throw new IllegalArgumentException("baseDelay must be positive");
        if (maxDelay == null || maxDelay.compareTo(baseDelay) < 0) throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        if (classifier == null) throw new IllegalArgumentException("classifier is required");

        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.classifier = classifier;
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        int attempt = 1;

        while (true) {
            try {
                return operation.call();
            } catch (Exception exception) {
                if (attempt >= maxAttempts || !classifier.isRetryable(exception)) {
                    throw exception;
                }

                Thread.sleep(delayForAttempt(attempt).toMillis());
                attempt++;
            }
        }
    }

    /**
     * delay = min(maxDelay, baseDelay * 2^attempt) + jitter
     */
    private Duration delayForAttempt(int attempt) {
        long exponentialMillis = baseDelay.toMillis()
                * (1L << Math.min(attempt - 1, 30));

        long cappedMillis = Math.min(maxDelay.toMillis(), exponentialMillis);

        long jitterMillis = ThreadLocalRandom.current()
                .nextLong(0, Math.max(1, baseDelay.toMillis()));

        return Duration.ofMillis(cappedMillis + jitterMillis);
    }
}