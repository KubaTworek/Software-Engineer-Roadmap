package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.saga;

import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.OrderCancelledTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.PaymentFailedTestEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.events.TestEventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support.InMemoryEventSink;

/**
 * Testable compensation handler.
 *
 * It reacts to PaymentFailed by publishing OrderCancelled.
 */
public class PaymentFailureCompensationHandler {

    private final InMemoryEventSink eventSink;

    public PaymentFailureCompensationHandler(InMemoryEventSink eventSink) {
        this.eventSink = eventSink;
    }

    /**
     * Handles payment failure by emitting a compensating order cancellation event.
     */
    public void handle(PaymentFailedTestEvent event) {
        OrderCancelledTestEvent compensation = new OrderCancelledTestEvent(
                TestEventMetadata.causedBy(event.metadata(), "order-service"),
                event.orderId(),
                "Payment failed: " + event.reason()
        );

        eventSink.publish(compensation);
    }
}