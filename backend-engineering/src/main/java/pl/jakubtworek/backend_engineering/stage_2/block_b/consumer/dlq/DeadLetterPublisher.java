package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.dlq;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;

/**
 * Publisher responsible for sending failed events to a dead-letter topic.
 *
 * DLQ prevents a single poisonous message from blocking the main processing pipeline.
 */
public interface DeadLetterPublisher<T extends ConsumedEvent> {

    /**
     * Publishes the failed event together with failure details.
     */
    void publish(T event, DeadLetterReason reason);
}