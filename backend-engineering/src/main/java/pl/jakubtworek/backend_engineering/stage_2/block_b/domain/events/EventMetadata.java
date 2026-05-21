package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata common to every event in the system.
 *
 * This object should not contain business data. It describes the event itself:
 * when it happened, who emitted it, how it can be correlated with other events,
 * and which schema version it follows.
 */
public record EventMetadata(

        /**
         * Unique identifier of this event.
         *
         * Consumers can use this value for idempotency checks.
         * For example, a consumer may store processed event IDs in a processed_events table.
         */
        UUID eventId,

        /**
         * Timestamp describing when the event occurred in the source service.
         *
         * This is not necessarily the same time as the moment when the event was published
         * to Kafka or consumed by another service.
         */
        Instant occurredAt,

        /**
         * Version of the event contract.
         *
         * This field helps consumers understand which schema version they are processing.
         */
        int version,

        /**
         * Identifier shared by all events belonging to the same business flow.
         *
         * In a simple order workflow, this can be equal to orderId.
         * In more complex systems, it can be a separate saga or trace identifier.
         */
        String correlationId,

        /**
         * Identifier of the event that caused this event.
         *
         * For example, PaymentAuthorized may reference the eventId of OrderPlaced.
         * This field is useful for reconstructing causal chains.
         */
        UUID causationId,

        /**
         * Name of the service that emitted the event.
         *
         * Example values: order-service, payment-service, shipping-service.
         */
        String sourceService
) {
    /**
     * Factory method for creating metadata for a new root event.
     *
     * A root event starts a new business flow and therefore usually has no causationId.
     */
    public static EventMetadata rootEvent(
            String correlationId,
            String sourceService,
            int version
    ) {
        return new EventMetadata(
                UUID.randomUUID(),
                Instant.now(),
                version,
                correlationId,
                null,
                sourceService
        );
    }

    /**
     * Factory method for creating metadata for an event caused by another event.
     *
     * This preserves the same correlationId but points to the previous event
     * through causationId.
     */
    public static EventMetadata causedBy(
            EventMetadata previousEventMetadata,
            String sourceService,
            int version
    ) {
        return new EventMetadata(
                UUID.randomUUID(),
                Instant.now(),
                version,
                previousEventMetadata.correlationId(),
                previousEventMetadata.eventId(),
                sourceService
        );
    }
}