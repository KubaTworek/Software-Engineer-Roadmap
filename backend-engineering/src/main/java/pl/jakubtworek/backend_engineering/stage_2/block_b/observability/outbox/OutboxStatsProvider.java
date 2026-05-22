package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.outbox;

/**
 * Provides monitoring data for an outbox table.
 *
 * Outbox growth may indicate that events are not being published to Kafka.
 */
public interface OutboxStatsProvider {

    /**
     * Returns number of unpublished outbox records.
     */
    long pendingEvents();

    /**
     * Returns age in seconds of the oldest unpublished event.
     */
    long oldestPendingEventAgeSeconds();
}