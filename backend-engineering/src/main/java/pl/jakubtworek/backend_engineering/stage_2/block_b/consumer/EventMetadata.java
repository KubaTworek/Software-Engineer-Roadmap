package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import java.time.Instant;
import java.util.UUID;

/**
 * Common metadata attached to every consumed domain event.
 *
 * Consumers should use eventId for idempotency, correlationId for tracing,
 * and occurredAt for understanding when the event happened in the producer service.
 */
public record EventMetadata(
        UUID eventId,
        Instant occurredAt,
        int version,
        String correlationId,
        UUID causationId,
        String sourceService
) {
}