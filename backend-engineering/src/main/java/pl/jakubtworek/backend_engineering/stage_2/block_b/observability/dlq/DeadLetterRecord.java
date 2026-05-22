package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.dlq;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a message moved to a dead-letter queue.
 *
 * DLQ records should contain enough context to diagnose and potentially
 * replay the failed event.
 */
public record DeadLetterRecord(
        UUID eventId,
        String eventType,
        String aggregateId,
        String correlationId,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String errorMessage,
        String exceptionClass,
        Instant failedAt
) {
}