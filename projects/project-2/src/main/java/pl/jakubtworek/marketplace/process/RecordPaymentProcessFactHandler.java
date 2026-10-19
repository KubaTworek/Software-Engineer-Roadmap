package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.PaymentReservedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordPaymentProcessFactHandler implements DomainEventHandler<PaymentReservedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordPaymentProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<PaymentReservedIntegrationEvent> eventType() { return PaymentReservedIntegrationEvent.class; }
    @Override public void handle(PaymentReservedIntegrationEvent event) {
        manager.paymentReserved(event.orderId(), event.correlationId(), event.eventId());
    }
}
