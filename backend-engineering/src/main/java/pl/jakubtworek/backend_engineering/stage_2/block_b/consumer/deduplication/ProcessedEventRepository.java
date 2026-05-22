package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication;

import java.util.UUID;

/**
 * Repository responsible for tracking already processed events.
 *
 * A typical implementation stores event IDs in a processed_events table
 * with event_id as a unique key.
 */
public interface ProcessedEventRepository {

    /**
     * Tries to mark the event as processed before business logic is executed.
     *
     * Returns true when the event was inserted successfully.
     * Returns false when the event ID already exists, which means the event is a duplicate.
     */
    boolean tryMarkAsProcessed(UUID eventId);

    /**
     * Removes the processed marker when business processing failed before completion.
     *
     * This is optional and depends on the transaction strategy.
     * If the deduplication insert and business effects are in the same database transaction,
     * rollback will handle this automatically.
     */
    void removeProcessedMarker(UUID eventId);
}