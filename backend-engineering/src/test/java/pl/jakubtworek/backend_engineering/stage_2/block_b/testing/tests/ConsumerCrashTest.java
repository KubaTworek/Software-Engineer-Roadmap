package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.tests;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer.FailingPaymentConsumer;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestEventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.kafka.ManualCommitConsumerHarness;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.kafka.TestOffsetTracker;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryPaymentRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryProcessedEventStore;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying behavior when a consumer crashes before committing offset.
 */
class ConsumerCrashTest {

    /**
     * If the consumer crashes before offset commit, the offset must remain uncommitted.
     */
    @Test
    void shouldNotCommitOffsetWhenConsumerCrashes() {
        InMemoryProcessedEventStore processedEventStore =
                new InMemoryProcessedEventStore();

        InMemoryPaymentRepository paymentRepository =
                new InMemoryPaymentRepository();

        FailingPaymentConsumer consumer =
                new FailingPaymentConsumer(processedEventStore, paymentRepository);

        consumer.failBeforeBusinessWrite();

        TestOffsetTracker offsetTracker = new TestOffsetTracker();

        ManualCommitConsumerHarness harness =
                new ManualCommitConsumerHarness(offsetTracker);

        OrderPlacedTestEvent event = new OrderPlacedTestEvent(
                TestEventMetadata.newEvent("ORD-12345", "order-service"),
                "ORD-12345",
                new BigDecimal("159.99")
        );

        harness.consume(event, 10, consumer::handle);

        assertFalse(offsetTracker.isCommitted());
    }

    /**
     * After restart, Kafka would redeliver the same event because offset was not committed.
     */
    @Test
    void shouldProcessAgainAfterRestartWhenOffsetWasNotCommitted() {
        InMemoryProcessedEventStore processedEventStore =
                new InMemoryProcessedEventStore();

        InMemoryPaymentRepository paymentRepository =
                new InMemoryPaymentRepository();

        FailingPaymentConsumer consumer =
                new FailingPaymentConsumer(processedEventStore, paymentRepository);

        consumer.failBeforeBusinessWrite();

        TestOffsetTracker offsetTracker = new TestOffsetTracker();

        ManualCommitConsumerHarness harness =
                new ManualCommitConsumerHarness(offsetTracker);

        OrderPlacedTestEvent event = new OrderPlacedTestEvent(
                TestEventMetadata.newEvent("ORD-12345", "order-service"),
                "ORD-12345",
                new BigDecimal("159.99")
        );

        harness.consume(event, 10, consumer::handle);
        harness.consume(event, 10, consumer::handle);

        assertTrue(paymentRepository.existsForOrder("ORD-12345"));
        assertTrue(offsetTracker.isCommitted());
    }
}