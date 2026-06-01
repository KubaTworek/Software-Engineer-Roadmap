package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

@Component
public class ReserveStockOnOrderPlacedHandler implements DomainEventHandler<OrderPlaced> {
    private final StockRepository repository;
    private final EventPublisher eventPublisher;

    public ReserveStockOnOrderPlacedHandler(StockRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
    }

    @Override
    @Transactional
    public void handle(OrderPlaced event) {
        // Szkielet: na starcie rezerwujemy symboliczny stock dla aggregateId.
        // Docelowo OrderPlaced powinien przenosić linie zamówienia w payloadzie eventu integracyjnego.
        StockItem item = repository.findByProductId(event.aggregateId()).orElseGet(() -> StockItem.create(event.aggregateId(), 100));
        item.reserve(event.aggregateId(), 1, event.correlationId(), event.eventId());
        repository.save(item);
        item.domainEvents().forEach(eventPublisher::publish);
        item.clearDomainEvents();
    }
}
