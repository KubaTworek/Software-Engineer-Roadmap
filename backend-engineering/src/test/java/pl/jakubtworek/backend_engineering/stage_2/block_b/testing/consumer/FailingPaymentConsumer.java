package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryPaymentRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryProcessedEventStore;

/**
 * Consumer that can intentionally fail during processing.
 *
 * This is useful for testing crash scenarios before Kafka offset commit.
 */
public class FailingPaymentConsumer {

    private final InMemoryProcessedEventStore processedEventStore;
    private final InMemoryPaymentRepository paymentRepository;
    private boolean failBeforeBusinessWrite;
    private boolean failAfterBusinessWrite;

    public FailingPaymentConsumer(
            InMemoryProcessedEventStore processedEventStore,
            InMemoryPaymentRepository paymentRepository
    ) {
        this.processedEventStore = processedEventStore;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Configures the consumer to fail before writing business state.
     */
    public void failBeforeBusinessWrite() {
        this.failBeforeBusinessWrite = true;
    }

    /**
     * Configures the consumer to fail after writing business state.
     */
    public void failAfterBusinessWrite() {
        this.failAfterBusinessWrite = true;
    }

    /**
     * Handles an event and optionally throws an exception at controlled points.
     *
     * Tests use this method to simulate a crash before offset commit.
     */
    public void handle(OrderPlacedTestEvent event) {
        boolean isNewEvent = processedEventStore.tryMarkProcessed(
                event.metadata().eventId()
        );

        if (!isNewEvent) {
            return;
        }

        if (failBeforeBusinessWrite) {
            failBeforeBusinessWrite = false;
            throw new SimulatedConsumerCrashException("Crash before business write.");
        }

        paymentRepository.createIfAbsent(
                event.orderId(),
                event.totalAmount()
        );

        if (failAfterBusinessWrite) {
            failAfterBusinessWrite = false;
            throw new SimulatedConsumerCrashException("Crash after business write.");
        }
    }
}