package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.kafka;

/**
 * Functional interface representing a test event handler.
 */
@FunctionalInterface
public interface EventConsumerFunction<T> {

    /**
     * Handles the event.
     */
    void handle(T event);
}