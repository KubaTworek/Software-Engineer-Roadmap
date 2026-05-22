package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.EventMetadata;

import java.util.UUID;

/**
 * Represents diagnostic context propagated while processing one event.
 *
 * This context should be attached to logs, traces and metrics so that
 * engineers can find all technical signals related to the same business flow.
 */
public record ObservabilityContext(
        UUID eventId,
        String correlationId,
        UUID causationId,
        String sourceService,
        String eventType,
        String aggregateId,
        String traceId
) {
    /**
     * Creates an observability context from event metadata and event identity.
     */
    public static ObservabilityContext from(
            EventMetadata metadata,
            String eventType,
            String aggregateId
    ) {
        return new ObservabilityContext(
                metadata.eventId(),
                metadata.correlationId(),
                metadata.causationId(),
                metadata.sourceService(),
                eventType,
                aggregateId,
                metadata.traceId()
        );
    }
}