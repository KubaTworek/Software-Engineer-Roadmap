package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;

import java.time.Duration;

/**
 * Executes event processing with retry support.
 *
 * This class is independent from Kafka and can be tested without a broker.
 */
public class RetryingEventProcessor<T extends ConsumedEvent> {

    private final RetryPolicy retryPolicy;
    private final SingleAttemptProcessor<T> singleAttemptProcessor;

    public RetryingEventProcessor(
            RetryPolicy retryPolicy,
            SingleAttemptProcessor<T> singleAttemptProcessor
    ) {
        this.retryPolicy = retryPolicy;
        this.singleAttemptProcessor = singleAttemptProcessor;
    }

    /**
     * Processes an event and retries only retryable failures.
     */
    public ProcessingResult processWithRetry(T event) {
        for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
            ProcessingResult result = singleAttemptProcessor.process(event);

            if (result == ProcessingResult.PROCESSED
                    || result == ProcessingResult.DUPLICATE_SKIPPED
                    || result == ProcessingResult.NON_RETRYABLE_FAILURE) {
                return result;
            }

            if (attempt < retryPolicy.maxAttempts()) {
                sleep(retryPolicy.backoffStrategy().calculateDelay(attempt));
            }
        }

        return ProcessingResult.NON_RETRYABLE_FAILURE;
    }

    /**
     * Sleeps before the next retry attempt.
     *
     * In production code, this could be replaced by a scheduler or retry topic pattern
     * instead of blocking the consumer thread.
     */
    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Retry sleep was interrupted.", exception);
        }
    }
}