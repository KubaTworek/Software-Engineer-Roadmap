package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructre.idempotency;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency.IdempotentEventConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency.ProcessedMessage;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency.ProcessedMessageRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Unit test for idempotent consumer.
// It verifies that duplicate messages are ignored.
class IdempotentEventConsumerTest {

    @Test
    void shouldProcessMessageOnlyOnce() {
        // Given
        InMemoryProcessedMessageRepository repository =
                new InMemoryProcessedMessageRepository();

        AtomicInteger handledCount = new AtomicInteger();

        IdempotentEventConsumer<String> consumer = new IdempotentEventConsumer<>(
                "fulfillment-order-placed-consumer",
                repository,
                event -> handledCount.incrementAndGet()
        );

        // When
        consumer.consume("MSG-1", "OrderPlaced");
        consumer.consume("MSG-1", "OrderPlaced");

        // Then
        assertEquals(1, handledCount.get());
    }

    private static final class InMemoryProcessedMessageRepository
            implements ProcessedMessageRepository {

        private final Set<String> processed = new HashSet<>();

        @Override
        public boolean alreadyProcessed(String consumerName, String messageId) {
            return processed.contains(consumerName + ":" + messageId);
        }

        @Override
        public void markAsProcessed(ProcessedMessage processedMessage) {
            processed.add(processedMessage.consumerName() + ":" + processedMessage.messageId());
        }
    }
}