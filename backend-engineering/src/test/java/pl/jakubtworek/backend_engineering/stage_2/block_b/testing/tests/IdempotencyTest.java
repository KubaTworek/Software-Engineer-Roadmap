package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.tests;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer.TestablePaymentConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestEventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryPaymentRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryProcessedEventStore;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests verifying idempotent processing of duplicated events.
 */
class IdempotencyTest {

    /**
     * Sending the same event twice should produce only one payment record.
     */
    @Test
    void shouldProcessDuplicatedEventOnlyOnce() {
        InMemoryProcessedEventStore processedEventStore =
                new InMemoryProcessedEventStore();

        InMemoryPaymentRepository paymentRepository =
                new InMemoryPaymentRepository();

        TestablePaymentConsumer consumer =
                new TestablePaymentConsumer(processedEventStore, paymentRepository);

        UUID duplicatedEventId = UUID.randomUUID();

        OrderPlacedTestEvent event = new OrderPlacedTestEvent(
                TestEventMetadata.withFixedEventId(
                        duplicatedEventId,
                        "ORD-12345",
                        "order-service"
                ),
                "ORD-12345",
                new BigDecimal("159.99")
        );

        consumer.handle(event);
        consumer.handle(event);

        assertEquals(1, processedEventStore.size());
        assertEquals(1, paymentRepository.count());
    }
}