package pl.jakubtworek.marketplace.integration.kafka;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Techniczny model wpisu processed_events.
 *
 * Reprezentuje fakt, że konkretny event został już przetworzony
 * przez konkretnego konsumenta.
 */
public record ProcessedEvent(
        UUID eventId,
        String consumerName,
        Instant processedAt
) {

    public ProcessedEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(consumerName, "consumerName cannot be null");
        Objects.requireNonNull(processedAt, "processedAt cannot be null");

        if (consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName cannot be blank");
        }
    }

    public static ProcessedEvent processed(UUID eventId, String consumerName) {
        return new ProcessedEvent(eventId, consumerName, Instant.now());
    }

    public static ProcessedEvent restore(UUID eventId, String consumerName, Instant processedAt) {
        return new ProcessedEvent(eventId, consumerName, processedAt);
    }
}