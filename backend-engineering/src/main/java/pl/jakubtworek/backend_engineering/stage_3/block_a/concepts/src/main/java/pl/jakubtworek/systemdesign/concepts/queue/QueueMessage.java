package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.queue;

import java.time.Instant;
import java.util.UUID;

/**
 * Queue message with idempotency key and creation timestamp.
 */
public record QueueMessage<T>(
        String messageId,
        T payload,
        Instant createdAt
) {
    public static <T> QueueMessage<T> of(T payload) {
        return new QueueMessage<>(
                UUID.randomUUID().toString(),
                payload,
                Instant.now()
        );
    }
}