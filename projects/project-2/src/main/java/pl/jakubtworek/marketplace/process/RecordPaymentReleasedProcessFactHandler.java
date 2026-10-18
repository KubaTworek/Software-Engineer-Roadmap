package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.PaymentReleasedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordPaymentReleasedProcessFactHandler implements DomainEventHandler<PaymentReleasedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordPaymentReleasedProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<PaymentReleasedIntegrationEvent> eventType() { return PaymentReleasedIntegrationEvent.class; }
    @Override public void handle(PaymentReleasedIntegrationEvent event) { manager.compensationCompleted(event.orderId()); }
}
