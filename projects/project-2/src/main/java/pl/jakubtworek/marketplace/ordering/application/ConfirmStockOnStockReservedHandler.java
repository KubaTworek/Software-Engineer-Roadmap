package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

@Component
public class ConfirmStockOnStockReservedHandler implements DomainEventHandler<StockReserved> {
    private final OrderRepository repository;
    private final EventPublisher eventPublisher;

    public ConfirmStockOnStockReservedHandler(OrderRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override public Class<StockReserved> eventType() { return StockReserved.class; }

    @Override
    @Transactional
    public void handle(StockReserved event) {
        var order = repository.findById(OrderId.of(event.orderId())).orElseThrow();
        order.markStockReserved(event.correlationId(), event.eventId());
        repository.save(order);
        var events = java.util.List.copyOf(order.domainEvents());
        order.clearDomainEvents();
        events.forEach(eventPublisher::publish);
    }
}
