package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.queue;

/**
 * Processes a queue message.
 *
 * Implementations must be idempotent because messages can be delivered more than once.
 */
@FunctionalInterface
public interface MessageHandler<T> {

    void handle(QueueMessage<T> message) throws Exception;
}