package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;

import java.time.Duration;
import java.util.Objects;

/**
 * Executes event processing with retry support.
 *
 * This class is independent from Kafka and can be tested without a broker.
 */
public class RetryingEventProcessor<T extends ConsumedEvent> {

    private final RetryPolicy retryPolicy;
    private final SingleAttemptProcessor<T> singleAttemptProcessor;
    private final RetrySleeper sleeper;

    public RetryingEventProcessor(
            RetryPolicy retryPolicy,
            SingleAttemptProcessor<T> singleAttemptProcessor
    ) {
        this(retryPolicy, singleAttemptProcessor, delay -> Thread.sleep(delay.toMillis()));
    }

    public RetryingEventProcessor(
            RetryPolicy retryPolicy,
            SingleAttemptProcessor<T> singleAttemptProcessor,
            RetrySleeper sleeper
    ) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.singleAttemptProcessor = Objects.requireNonNull(
                singleAttemptProcessor, "singleAttemptProcessor must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    /**
     * Processes an event and retries only retryable failures.
     */
    public ProcessingResult processWithRetry(T event) {
        return processWithReport(event).result();
    }

    /** Returns the terminal classification together with the real attempt count. */
    public RetryOutcome processWithReport(T event) {
        Objects.requireNonNull(event, "event must not be null");
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            ProcessingResult result = singleAttemptProcessor.process(event);

            if (result == ProcessingResult.PROCESSED
                    || result == ProcessingResult.DUPLICATE_SKIPPED
                    || result == ProcessingResult.NON_RETRYABLE_FAILURE) {
                return new RetryOutcome(result, attempt);
            }

            if (attempt < retryPolicy.maxAttempts()) {
                sleep(retryPolicy.backoffStrategy().calculateDelay(attempt));
            }
        }

        return new RetryOutcome(ProcessingResult.RETRIES_EXHAUSTED, retryPolicy.maxAttempts());
    }

    /**
     * Sleeps before the next retry attempt.
     *
     * In production code, this could be replaced by a scheduler or retry topic pattern
     * instead of blocking the consumer thread.
     */
    private void sleep(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Retry sleep was interrupted.", exception);
        }
    }
}
