package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.StockReservedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordStockProcessFactHandler implements DomainEventHandler<StockReservedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordStockProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<StockReservedIntegrationEvent> eventType() { return StockReservedIntegrationEvent.class; }
    @Override public void handle(StockReservedIntegrationEvent event) {
        manager.stockReserved(event.orderId(), event.correlationId(), event.eventId());
    }
}
