package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.queue;

/**
 * Handler for one queue message.
 *
 * Implementations should be idempotent.
 */
@FunctionalInterface
public interface MessageHandler<T> {

    void handle(QueueMessage<T> message) throws Exception;
}