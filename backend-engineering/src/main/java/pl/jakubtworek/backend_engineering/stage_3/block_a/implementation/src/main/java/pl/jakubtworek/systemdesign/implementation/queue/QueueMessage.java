package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.queue;

import java.time.Instant;
import java.util.UUID;

/**
 * Queue message wrapper.
 *
 * messageId is used for idempotency.
 * createdAt is used for oldest-message-age monitoring.
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