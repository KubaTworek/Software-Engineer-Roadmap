package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import java.time.Instant;
import java.util.Objects;
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
    public EventMetadata {
        eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be greater than zero");
        }
        correlationId = requireNonBlank(correlationId, "correlationId");
        sourceService = requireNonBlank(sourceService, "sourceService");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
