package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple idempotent queue worker.
 *
 * This example keeps processed IDs in memory only.
 * In production, idempotency state should be stored in a durable store.
 */
public class IdempotentQueueWorker<T> {

    private final MessageHandler<T> handler;
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public IdempotentQueueWorker(MessageHandler<T> handler) {
        this.handler = handler;
    }

    public void process(QueueMessage<T> message) throws Exception {
        if (!processedMessageIds.add(message.messageId())) {
            return;
        }

        handler.handle(message);
    }

    public Duration messageAge(QueueMessage<T> message) {
        return Duration.between(message.createdAt(), Instant.now());
    }
}