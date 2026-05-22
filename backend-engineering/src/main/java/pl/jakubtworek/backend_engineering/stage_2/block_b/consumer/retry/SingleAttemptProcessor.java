package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;

/**
 * Represents one processing attempt for a consumed event.
 */
public interface SingleAttemptProcessor<T extends ConsumedEvent> {

    /**
     * Processes the event once and returns the result.
     */
    ProcessingResult process(T event);
}