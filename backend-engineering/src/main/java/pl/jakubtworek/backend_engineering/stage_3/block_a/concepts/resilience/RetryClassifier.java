package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience;

/**
 * Decides whether an exception should be retried.
 */
@FunctionalInterface
public interface RetryClassifier {

    boolean isRetryable(Exception exception);
}