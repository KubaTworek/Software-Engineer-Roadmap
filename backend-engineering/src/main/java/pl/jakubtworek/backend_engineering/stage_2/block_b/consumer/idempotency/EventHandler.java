package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;

/**
 * Business handler for a specific event type.
 *
 * Implementations should contain domain logic, not Kafka offset management.
 */
public interface EventHandler<T extends ConsumedEvent> {

    /**
     * Handles the event and applies business side effects.
     */
    void handle(T event);
}