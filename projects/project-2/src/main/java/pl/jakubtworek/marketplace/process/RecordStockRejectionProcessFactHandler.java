package pl.jakubtworek.marketplace.process;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.StockReservationFailedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RecordStockRejectionProcessFactHandler implements DomainEventHandler<StockReservationFailedIntegrationEvent> {
    private final OrderFulfillmentProcessManager manager;
    public RecordStockRejectionProcessFactHandler(OrderFulfillmentProcessManager manager) { this.manager = manager; }
    @Override public Class<StockReservationFailedIntegrationEvent> eventType() { return StockReservationFailedIntegrationEvent.class; }
    @Override public void handle(StockReservationFailedIntegrationEvent event) {
        manager.stockRejected(event.orderId(), event.correlationId(), event.eventId());
    }
}
