package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata shared by all test events.
 *
 * In tests, eventId is especially important because it allows us to verify
 * whether idempotency and deduplication work correctly.
 */
public record TestEventMetadata(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID causationId,
        String sourceService
) {
    /**
     * Creates metadata for a new test event.
     */
    public static TestEventMetadata newEvent(String correlationId, String sourceService) {
        return new TestEventMetadata(
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                null,
                sourceService
        );
    }

    /**
     * Creates metadata with a fixed eventId.
     *
     * This is useful when testing duplicated event delivery.
     */
    public static TestEventMetadata withFixedEventId(
            UUID eventId,
            String correlationId,
            String sourceService
    ) {
        return new TestEventMetadata(
                eventId,
                Instant.now(),
                correlationId,
                null,
                sourceService
        );
    }

    /**
     * Creates metadata for an event caused by another event.
     */
    public static TestEventMetadata causedBy(
            TestEventMetadata previous,
            String sourceService
    ) {
        return new TestEventMetadata(
                UUID.randomUUID(),
                Instant.now(),
                previous.correlationId(),
                previous.eventId(),
                sourceService
        );
    }
}