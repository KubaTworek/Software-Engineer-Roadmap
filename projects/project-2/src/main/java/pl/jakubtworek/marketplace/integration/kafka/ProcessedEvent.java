package pl.jakubtworek.marketplace.integration.kafka;

import java.time.Instant;
import java.util.UUID;

public record ProcessedEvent(
        UUID eventId,
        String consumerName,
        ProcessedEventStatus status,
        Instant processedAt,
        String error
) {
    public static ProcessedEvent processed(UUID eventId, String consumerName) {
        return new ProcessedEvent(eventId, consumerName, ProcessedEventStatus.PROCESSED, Instant.now(), null);
    }
}
