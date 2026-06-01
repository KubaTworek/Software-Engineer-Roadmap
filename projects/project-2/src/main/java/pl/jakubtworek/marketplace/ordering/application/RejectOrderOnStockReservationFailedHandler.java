package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;

@Component
public class RejectOrderOnStockReservationFailedHandler implements DomainEventHandler<StockReservationFailed> {
    private final OrderRepository repository;

    public RejectOrderOnStockReservationFailedHandler(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Class<StockReservationFailed> eventType() {
        return StockReservationFailed.class;
    }

    @Override
    @Transactional
    public void handle(StockReservationFailed event) {
        var order = repository.findById(OrderId.of(event.orderId())).orElseThrow();
        order.reject(event.reason());
        repository.save(order);
        order.clearDomainEvents();
    }
}
