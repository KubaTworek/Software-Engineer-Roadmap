package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.queue;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotent queue worker.
 *
 * This example stores processed IDs in memory and therefore deduplicates only
 * within one running JVM. A production consumer needs a durable, atomic claim
 * tied to the business effect; otherwise a restart or a crash between the
 * effect and acknowledgement can still produce duplicates.
 */
public class IdempotentQueueWorker<T> {

    private final MessageHandler<T> handler;
    private final Clock clock;
    private final Set<String> processedIds = ConcurrentHashMap.newKeySet();

    public IdempotentQueueWorker(MessageHandler<T> handler) {
        this(handler, Clock.systemUTC());
    }

    public IdempotentQueueWorker(MessageHandler<T> handler, Clock clock) {
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void process(QueueMessage<T> message) throws Exception {
        Objects.requireNonNull(message, "message must not be null");
        if (!processedIds.add(message.messageId())) {
            return;
        }

        try {
            handler.handle(message);
        } catch (Exception exception) {
            processedIds.remove(message.messageId());
            throw exception;
        }
    }

    public Duration messageAge(QueueMessage<T> message) {
        Objects.requireNonNull(message, "message must not be null");
        Duration age = Duration.between(message.createdAt(), Instant.now(clock));
        return age.isNegative() ? Duration.ZERO : age;
    }
}
