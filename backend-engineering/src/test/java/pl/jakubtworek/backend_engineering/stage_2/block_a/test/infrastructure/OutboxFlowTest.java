package pl.jakubtworek.backend_engineering.stage_2.block_a.test.infrastructure;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.messaging.MessageBroker;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxMessage;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxMessageRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxRelay;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.SalesIntegrationEventMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxFlowTest {

    @Test
    void shouldStoreDomainEventAndPublishItWithAggregateAsMessageKey() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        OutboxEventPublisher publisher = new OutboxEventPublisher(
                repository,
                new SalesIntegrationEventMapper(),
                event -> "serialized-event"
        );
        RecordingBroker broker = new RecordingBroker();
        OrderPlacedEvent event = OrderPlacedEvent.now(
                OrderId.of("O-1"),
                CustomerId.of("C-1"),
                Money.of(new BigDecimal("25.00"), Currency.getInstance("PLN"))
        );

        publisher.publish(event);
        OutboxMessage stored = repository.messages.get(0);
        assertFalse(stored.published());
        assertEquals("sales.order-placed.v1", stored.eventType());

        new OutboxRelay(repository, broker).publishPendingMessages();

        assertEquals(List.of("sales.order-events|O-1|serialized-event"), broker.messages);
        assertTrue(stored.published());
    }

    @Test
    void shouldLeaveMessagePendingWhenBrokerFailsSoNextRunCanRetryIt() {
        InMemoryOutboxRepository repository = new InMemoryOutboxRepository();
        OutboxMessage message = new OutboxMessage(
                "event-1", "O-1", "sales.order-placed.v1", "payload", Instant.now()
        );
        repository.save(message);
        MessageBroker failingBroker = (topic, key, payload) -> {
            throw new IllegalStateException("broker unavailable");
        };

        assertThrows(
                IllegalStateException.class,
                () -> new OutboxRelay(repository, failingBroker).publishPendingMessages()
        );
        assertFalse(message.published());

        RecordingBroker recoveredBroker = new RecordingBroker();
        new OutboxRelay(repository, recoveredBroker).publishPendingMessages();

        assertEquals(1, recoveredBroker.messages.size());
        assertTrue(message.published());
    }

    private static final class InMemoryOutboxRepository implements OutboxMessageRepository {

        private final List<OutboxMessage> messages = new ArrayList<>();

        @Override
        public void save(OutboxMessage message) {
            if (!messages.contains(message)) {
                messages.add(message);
            }
        }

        @Override
        public List<OutboxMessage> findUnpublished(int limit) {
            return messages.stream()
                    .filter(message -> !message.published())
                    .limit(limit)
                    .toList();
        }
    }

    private static final class RecordingBroker implements MessageBroker {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(String topic, String key, String payload) {
            messages.add(topic + "|" + key + "|" + payload);
        }
    }
}
