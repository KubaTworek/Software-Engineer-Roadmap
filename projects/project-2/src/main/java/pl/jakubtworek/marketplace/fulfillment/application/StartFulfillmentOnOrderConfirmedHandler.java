package pl.jakubtworek.marketplace.fulfillment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.ordering.domain.OrderConfirmed;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class StartFulfillmentOnOrderConfirmedHandler implements DomainEventHandler<OrderConfirmed> {
    private static final Logger log = LoggerFactory.getLogger(StartFulfillmentOnOrderConfirmedHandler.class);

    @Override public Class<OrderConfirmed> eventType() { return OrderConfirmed.class; }

    @Override
    public void handle(OrderConfirmed event) {
        log.info("Starting fulfillment for orderId={}, correlationId={}", event.aggregateId(), event.correlationId());
    }
}
