package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory processed event store used for unit tests.
 *
 * Production systems should use a durable database table with a unique index
 * on event_id instead.
 */
public class InMemoryProcessedEventStore {

    private final Set<UUID> processedEventIds = new HashSet<>();

    /**
     * Tries to register the event as processed.
     *
     * Returns false when the same eventId was already processed before.
     */
    public boolean tryMarkProcessed(UUID eventId) {
        return processedEventIds.add(eventId);
    }

    /**
     * Returns the number of unique processed event IDs.
     */
    public int size() {
        return processedEventIds.size();
    }

    /**
     * Clears the store between tests.
     */
    public void clear() {
        processedEventIds.clear();
    }
}