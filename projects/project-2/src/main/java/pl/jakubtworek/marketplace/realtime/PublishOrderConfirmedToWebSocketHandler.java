package pl.jakubtworek.marketplace.realtime;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.OrderConfirmedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class PublishOrderConfirmedToWebSocketHandler implements DomainEventHandler<OrderConfirmedIntegrationEvent> {
    private final OrderWebSocketHub hub;
    public PublishOrderConfirmedToWebSocketHandler(OrderWebSocketHub hub) { this.hub = hub; }
    @Override public Class<OrderConfirmedIntegrationEvent> eventType() { return OrderConfirmedIntegrationEvent.class; }
    @Override public void handle(OrderConfirmedIntegrationEvent event) {
        hub.orderChanged(event.aggregateId(),
                "{\"type\":\"ORDER_CONFIRMED\",\"orderId\":\"" + event.aggregateId() + "\"}", event.occurredAt());
    }
}
