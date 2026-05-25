package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotent queue worker.
 *
 * This example stores processed IDs in memory.
 * Production systems should store idempotency state durably.
 */
public class IdempotentQueueWorker<T> {

    private final MessageHandler<T> handler;
    private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

    public IdempotentQueueWorker(MessageHandler<T> handler) {
        this.handler = handler;
    }

    public void process(QueueMessage<T> message) throws Exception {
        if (!processedIds.add(message.messageId())) {
            return;
        }

        handler.handle(message);
    }

    public Duration messageAge(QueueMessage<T> message) {
        return Duration.between(message.createdAt(), Instant.now());
    }
}