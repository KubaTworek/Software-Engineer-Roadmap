package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.ArrayList;
import java.util.List;

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
        for (OrderPlaced.Line line : event.lines()) {
            var item = repository.findByProductId(line.productId());
            if (item.isEmpty()) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(), line.productId(), "Missing stock item", event.correlationId(), event.eventId()));
                return;
            }
            if (!item.get().canReserve(line.quantity())) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(), line.productId(), "Not enough stock", event.correlationId(), event.eventId()));
                return;
            }
        }

        List<StockReserved.Line> reservedLines = new ArrayList<>();
        for (OrderPlaced.Line line : event.lines()) {
            var item = repository.findByProductId(line.productId()).orElseThrow();
            item.reserveWithoutPublishingEvent(line.quantity());
            repository.save(item);
            reservedLines.add(new StockReserved.Line(line.productId(), line.quantity()));
        }

        eventPublisher.publish(StockReserved.now(event.aggregateId(), reservedLines, event.correlationId(), event.eventId()));
    }
}
