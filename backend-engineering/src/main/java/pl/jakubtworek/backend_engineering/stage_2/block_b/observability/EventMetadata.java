package pl.jakubtworek.backend_engineering.stage_2.block_b.observability;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata attached to every event flowing through the system.
 *
 * Observability depends heavily on this metadata because it allows logs,
 * metrics and traces from different services to be connected into one
 * business-level story.
 */
public record EventMetadata(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        UUID causationId,
        String sourceService,
        String traceId
) {
}