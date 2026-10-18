package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.PaymentRejectedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordPaymentRejectionProcessFactHandler implements DomainEventHandler<PaymentRejectedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordPaymentRejectionProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<PaymentRejectedIntegrationEvent> eventType() { return PaymentRejectedIntegrationEvent.class; }
    @Override public void handle(PaymentRejectedIntegrationEvent event) {
        manager.paymentRejected(event.orderId(), event.correlationId(), event.eventId());
    }
}
