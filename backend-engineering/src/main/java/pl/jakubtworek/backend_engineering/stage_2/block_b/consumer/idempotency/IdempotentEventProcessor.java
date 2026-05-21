package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

import com.example.ecommerce.consumer.ConsumedEvent;

/**
 * Generic wrapper for idempotent event handling.
 *
 * The wrapper decides whether the event is new or duplicated before delegating
 * to the actual business handler.
 */
public interface IdempotentEventProcessor<T extends ConsumedEvent> {

    /**
     * Processes the event only if it has not been processed before.
     *
     * Duplicate events should be skipped without executing business side effects again.
     */
    ProcessingResult process(T event);
}