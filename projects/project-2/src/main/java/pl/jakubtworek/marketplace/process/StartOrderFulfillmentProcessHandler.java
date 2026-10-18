package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.OrderPlacedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class StartOrderFulfillmentProcessHandler implements DomainEventHandler<OrderPlacedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public StartOrderFulfillmentProcessHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<OrderPlacedIntegrationEvent> eventType() { return OrderPlacedIntegrationEvent.class; }
    @Override public void handle(OrderPlacedIntegrationEvent event) { manager.started(event.aggregateId(), event.occurredAt()); }
}
