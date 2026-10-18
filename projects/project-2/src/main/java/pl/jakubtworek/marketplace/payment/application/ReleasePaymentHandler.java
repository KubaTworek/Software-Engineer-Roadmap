package pl.jakubtworek.marketplace.payment.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import pl.jakubtworek.marketplace.integration.contracts.PaymentReleaseRequestedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.payment.domain.PaymentReleased;

/** Wykonuje realny lokalny efekt kompensacji i toleruje redelivery komendy. */
@Component
public class ReleasePaymentHandler implements DomainEventHandler<PaymentReleaseRequestedIntegrationEvent> {
    private final PaymentRepository payments;
    private final EventPublisher events;
    @Autowired
    public ReleasePaymentHandler(PaymentRepository payments, EventPublisher events) {
        this.payments = payments; this.events = events;
    }
    public ReleasePaymentHandler(PaymentRepository payments) { this(payments, ignored -> { }); }
    @Override public Class<PaymentReleaseRequestedIntegrationEvent> eventType() {
        return PaymentReleaseRequestedIntegrationEvent.class;
    }
    @Override @Transactional
    public void handle(PaymentReleaseRequestedIntegrationEvent event) {
        var payment = payments.findByOrderId(event.orderId()).orElseThrow();
        if (payment.release()) {
            payments.save(payment);
            events.publish(PaymentReleased.now(payment, event.correlationId(), event.eventId()));
        }
    }
}
