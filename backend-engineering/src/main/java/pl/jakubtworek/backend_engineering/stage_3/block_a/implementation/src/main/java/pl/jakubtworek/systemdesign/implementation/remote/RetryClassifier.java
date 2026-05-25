package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

/**
 * Determines whether an exception is retryable.
 */
@FunctionalInterface
public interface RetryClassifier {

    boolean isRetryable(Exception exception);
}