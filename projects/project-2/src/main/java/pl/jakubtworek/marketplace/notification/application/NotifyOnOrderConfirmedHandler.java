package pl.jakubtworek.marketplace.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.OrderConfirmedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class NotifyOnOrderConfirmedHandler implements DomainEventHandler<OrderConfirmedIntegrationEvent> {
    private static final Logger log = LoggerFactory.getLogger(NotifyOnOrderConfirmedHandler.class);

    @Override public Class<OrderConfirmedIntegrationEvent> eventType() { return OrderConfirmedIntegrationEvent.class; }

    @Override
    public void handle(OrderConfirmedIntegrationEvent event) {
        log.info("Notification requested for confirmed orderId={}, correlationId={}", event.aggregateId(), event.correlationId());
    }
}
