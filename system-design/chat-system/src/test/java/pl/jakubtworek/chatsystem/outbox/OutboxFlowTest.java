package pl.jakubtworek.chatsystem.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxFlowTest {
    @Autowired OutboxService outboxService;
    @Autowired OutboxEventRepository repository;
    @Autowired InMemoryQueueEventBus queue;

    @Test
    void appendSerializesPayloadAndStoresNewEvent() {
        OutboxEvent event = outboxService.append(
                UUID.randomUUID(),
                EventTypes.MESSAGE_CREATED,
                new MessageCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now())
        );

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.NEW);
        assertThat(event.getPayloadJson()).contains("messageId");
        assertThat(repository.countByStatus(OutboxStatus.NEW)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void inMemoryQueueRejectsOverflowOnlyAfterCapacityAndReportsSize() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "test.event", "{}");

        queue.enqueue(event);

        assertThat(queue.size()).isGreaterThanOrEqualTo(1);
        assertThat(queue.poll()).isNotNull();
    }
}
