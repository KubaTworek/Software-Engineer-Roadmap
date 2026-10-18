package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.StockReleasedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordStockReleasedProcessFactHandler implements DomainEventHandler<StockReleasedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordStockReleasedProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<StockReleasedIntegrationEvent> eventType() { return StockReleasedIntegrationEvent.class; }
    @Override public void handle(StockReleasedIntegrationEvent event) { manager.compensationCompleted(event.orderId()); }
}
