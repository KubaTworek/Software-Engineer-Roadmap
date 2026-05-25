package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry executor with exponential backoff and jitter.
 *
 * Use it only for transient failures and idempotent operations,
 * or operations protected by idempotency keys.
 */
public class RetryExecutor {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final RetryClassifier retryClassifier;

    public RetryExecutor(
            int maxAttempts,
            Duration baseDelay,
            Duration maxDelay,
            RetryClassifier retryClassifier
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

        if (retryClassifier == null) {
            throw new IllegalArgumentException("retryClassifier is required");
        }

        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.retryClassifier = retryClassifier;
    }

    public <T> T execute(Callable<T> operation) throws Exception {
        int attempt = 1;

        while (true) {
            try {
                return operation.call();
            } catch (Exception exception) {
                if (attempt >= maxAttempts || !retryClassifier.isRetryable(exception)) {
                    throw exception;
                }

                sleep(backoffWithJitter(attempt));
                attempt++;
            }
        }
    }

    private Duration backoffWithJitter(int attempt) {
        long exponentialMillis = baseDelay.toMillis() * (1L << Math.min(attempt - 1, 30));
        long cappedMillis = Math.min(maxDelay.toMillis(), exponentialMillis);
        long jitterMillis = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseDelay.toMillis()));

        return Duration.ofMillis(cappedMillis + jitterMillis);
    }

    private void sleep(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}