package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.payments;

import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.EventMetadata;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.order.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment.PaymentAuthorized;
import pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events.payment.PaymentFailed;

/**
 * Factory responsible for creating payment-related integration events.
 */
public class PaymentEventFactory {

    private static final String SOURCE_SERVICE = "payment-service";

    /**
     * Creates a PaymentAuthorized event caused by OrderPlaced.
     *
     * The new event keeps the same correlationId but points to the previous
     * event through causationId.
     */
    public PaymentAuthorized paymentAuthorized(
            Payment payment,
            OrderPlaced causedBy
    ) {
        EventMetadata metadata = EventMetadata.causedBy(
                causedBy.metadata(),
                SOURCE_SERVICE,
                PaymentAuthorized.VERSION
        );

        return new PaymentAuthorized(
                metadata,
                payment.orderId(),
                payment.paymentId(),
                payment.amount(),
                payment.status().name()
        );
    }

    /**
     * Creates a PaymentFailed event caused by OrderPlaced.
     */
    public PaymentFailed paymentFailed(
            Payment payment,
            OrderPlaced causedBy
    ) {
        EventMetadata metadata = EventMetadata.causedBy(
                causedBy.metadata(),
                SOURCE_SERVICE,
                PaymentFailed.VERSION
        );

        return new PaymentFailed(
                metadata,
                payment.orderId(),
                payment.paymentId(),
                payment.failureReason()
        );
    }
}