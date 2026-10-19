package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.queue;

import java.time.Instant;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Queue message with idempotency key and creation timestamp.
 */
public record QueueMessage<T>(
        String messageId,
        T payload,
        Instant createdAt
) {
    public QueueMessage {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("messageId is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
    }

    public static <T> QueueMessage<T> of(T payload) {
        return of(payload, Clock.systemUTC());
    }

    public static <T> QueueMessage<T> of(T payload, Clock clock) {
        return new QueueMessage<>(
                UUID.randomUUID().toString(),
                payload,
                Instant.now(Objects.requireNonNull(clock, "clock must not be null"))
        );
    }
}
