package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.messaging;

import java.time.Instant;
import java.util.Map;

// Envelope wraps the event with metadata needed for routing, tracing, and idempotency.
public record EventEnvelope(
        String messageId,
        String eventType,
        int version,
        String aggregateId,
        String correlationId,
        String causationId,
        Instant occurredAt,
        Map<String, String> headers,
        String payload
) {
}