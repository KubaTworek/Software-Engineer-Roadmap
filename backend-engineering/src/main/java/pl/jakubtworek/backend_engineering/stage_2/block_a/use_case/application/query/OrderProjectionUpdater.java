package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.query;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;

// Projection updater for the read side.
// It listens to domain or integration events and updates the read model.
public final class OrderProjectionUpdater {

    private final OrderReadModelRepository repository;

    public OrderProjectionUpdater(OrderReadModelRepository repository) {
        this.repository = repository;
    }

    public void on(OrderPlacedEvent event) {
        OrderReadModel readModel = new OrderReadModel(
                event.orderId().value(),
                event.customerId().value(),
                "PLACED",
                event.total().amount(),
                event.total().currency().getCurrencyCode(),
                event.occurredAt()
        );

        repository.save(readModel);
    }
}