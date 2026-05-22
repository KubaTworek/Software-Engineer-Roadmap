package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.tests;


import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.broker.FakeKafkaBroker;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestEventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.outbox.InMemoryOutbox;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.outbox.OutboxPublisher;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying the outbox behavior during broker outages.
 */
class OutboxBrokerFailureTest {

    /**
     * When Kafka is unavailable, the event must stay in the outbox.
     * After broker recovery, the same event should be published successfully.
     */
    @Test
    void shouldPublishOutboxEventAfterBrokerRecovery() {
        InMemoryOutbox outbox = new InMemoryOutbox();
        FakeKafkaBroker broker = new FakeKafkaBroker();
        OutboxPublisher publisher = new OutboxPublisher(outbox, broker);

        OrderPlacedTestEvent event = new OrderPlacedTestEvent(
                TestEventMetadata.newEvent("ORD-12345", "order-service"),
                "ORD-12345",
                new BigDecimal("159.99")
        );

        outbox.add(event);

        broker.shutdown();
        publisher.publishPendingEvents();

        assertEquals(0, broker.publishedCount());

        broker.start();
        publisher.publishPendingEvents();

        assertEquals(1, broker.publishedCount());
        assertTrue(outbox.allPublished());
    }
}