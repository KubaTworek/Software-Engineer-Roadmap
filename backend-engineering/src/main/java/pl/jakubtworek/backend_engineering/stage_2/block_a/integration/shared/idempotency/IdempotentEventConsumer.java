package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency;

import java.time.Instant;

// Base wrapper for idempotent event handling.
// It ensures that the same message is not processed twice by the same consumer.
public final class IdempotentEventConsumer<T> {

    private final String consumerName;
    private final ProcessedMessageRepository repository;
    private final EventHandler<T> handler;

    public IdempotentEventConsumer(
            String consumerName,
            ProcessedMessageRepository repository,
            EventHandler<T> handler
    ) {
        this.consumerName = consumerName;
        this.repository = repository;
        this.handler = handler;
    }

    public void consume(String messageId, T event) {
        if (repository.alreadyProcessed(consumerName, messageId)) {
            return;
        }

        handler.handle(event);

        repository.markAsProcessed(new ProcessedMessage(
                consumerName,
                messageId,
                Instant.now()
        ));
    }
}