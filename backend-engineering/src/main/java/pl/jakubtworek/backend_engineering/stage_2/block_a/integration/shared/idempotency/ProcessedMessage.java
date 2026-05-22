package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency;

import java.time.Instant;

// Stores information that a message has already been processed by a consumer.
// It is used to ignore duplicate messages delivered by the broker.
public record ProcessedMessage(
        String consumerName,
        String messageId,
        Instant processedAt
) {
}