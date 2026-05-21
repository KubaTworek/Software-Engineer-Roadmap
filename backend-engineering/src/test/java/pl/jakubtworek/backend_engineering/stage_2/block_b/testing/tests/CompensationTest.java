package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.tests;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderCancelledTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.PaymentFailedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestEventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.saga.PaymentFailureCompensationHandler;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryEventSink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying compensation behavior in a saga.
 */
class CompensationTest {

    /**
     * PaymentFailed should produce OrderCancelled as a compensating event.
     */
    @Test
    void shouldEmitOrderCancelledWhenPaymentFails() {
        InMemoryEventSink eventSink = new InMemoryEventSink();

        PaymentFailureCompensationHandler handler =
                new PaymentFailureCompensationHandler(eventSink);

        PaymentFailedTestEvent paymentFailed = new PaymentFailedTestEvent(
                TestEventMetadata.newEvent("ORD-12345", "payment-service"),
                "ORD-12345",
                "PAY-999",
                "Card rejected"
        );

        handler.handle(paymentFailed);

        assertEquals(1, eventSink.allEvents().size());

        assertTrue(eventSink.contains(event ->
                event instanceof OrderCancelledTestEvent
                        && event.aggregateId().equals("ORD-12345")
        ));
    }
}