package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.processing;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.ObservableEvent;

/**
 * Business event handler.
 *
 * Implementations should contain domain logic and should not duplicate
 * logging, metrics or tracing concerns.
 */
public interface EventHandler<T extends ObservableEvent> {

    /**
     * Handles a single event.
     */
    void handle(T event);
}