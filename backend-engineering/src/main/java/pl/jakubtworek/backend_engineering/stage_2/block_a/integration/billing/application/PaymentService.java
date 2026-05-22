package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.billing.application;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.PaymentCompletedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.PaymentFailedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox.TransactionalOutboxPublisher;

import java.time.Instant;
import java.util.UUID;

// Billing application service responsible for payment processing.
// It reacts to OrderPlaced and emits either PaymentCompleted or PaymentFailed.
public final class PaymentService {

    private final PaymentGateway paymentGateway;
    private final TransactionalOutboxPublisher eventPublisher;

    public PaymentService(
            PaymentGateway paymentGateway,
            TransactionalOutboxPublisher eventPublisher
    ) {
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    public void authorizePayment(OrderPlacedEvent event, String correlationId) {
        PaymentResult result = paymentGateway.authorize(
                event.orderId(),
                event.customerId(),
                event.totalAmount(),
                event.currency()
        );

        if (result.successful()) {
            eventPublisher.publish(new PaymentCompletedEvent(
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    result.paymentId(),
                    event.totalAmount(),
                    event.currency(),
                    Instant.now()
            ), correlationId);
        } else {
            eventPublisher.publish(new PaymentFailedEvent(
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    result.paymentId(),
                    result.failureReason(),
                    Instant.now()
            ), correlationId);
        }
    }
}