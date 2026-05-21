package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.consumer;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderPlacedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryPaymentRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryProcessedEventStore;

/**
 * Testable payment consumer.
 *
 * This class models the important behavior:
 * duplicated events must not produce duplicated business effects.
 */
public class TestablePaymentConsumer {

    private final InMemoryProcessedEventStore processedEventStore;
    private final InMemoryPaymentRepository paymentRepository;

    public TestablePaymentConsumer(
            InMemoryProcessedEventStore processedEventStore,
            InMemoryPaymentRepository paymentRepository
    ) {
        this.processedEventStore = processedEventStore;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Handles OrderPlaced in an idempotent way.
     *
     * If the eventId has already been processed, the event is skipped.
     */
    public void handle(OrderPlacedTestEvent event) {
        boolean isNewEvent = processedEventStore.tryMarkProcessed(
                event.metadata().eventId()
        );

        if (!isNewEvent) {
            return;
        }

        paymentRepository.createIfAbsent(
                event.orderId(),
                event.totalAmount()
        );
    }
}