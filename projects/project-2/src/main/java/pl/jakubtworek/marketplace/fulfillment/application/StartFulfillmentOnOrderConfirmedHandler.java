package pl.jakubtworek.marketplace.fulfillment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.contracts.OrderConfirmedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.fulfillment.domain.Shipment;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StartFulfillmentOnOrderConfirmedHandler implements DomainEventHandler<OrderConfirmedIntegrationEvent> {
    private static final Logger log = LoggerFactory.getLogger(StartFulfillmentOnOrderConfirmedHandler.class);
    private final ShipmentRepository shipments;

    public StartFulfillmentOnOrderConfirmedHandler(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override public Class<OrderConfirmedIntegrationEvent> eventType() { return OrderConfirmedIntegrationEvent.class; }

    @Override
    @Transactional
    public void handle(OrderConfirmedIntegrationEvent event) {
        /*
         * Nie wykonujemy najpierw exists(), a potem insert(). Dwa duplikaty mogłyby
         * przejść check równocześnie. Unikalne fulfillment.shipments.order_id oraz
         * saveIfAbsent tworzą dokładnie jeden lokalny efekt dla OrderConfirmed.
         */
        shipments.saveIfAbsent(Shipment.start(event.aggregateId()));
        log.info("Starting fulfillment for orderId={}, correlationId={}", event.aggregateId(), event.correlationId());
    }
}
