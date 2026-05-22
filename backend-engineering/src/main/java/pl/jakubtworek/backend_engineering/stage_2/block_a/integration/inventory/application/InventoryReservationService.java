package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.inventory.application;

import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.InventoryReservationFailedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.InventoryReservedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox.TransactionalOutboxPublisher;

import java.time.Instant;
import java.util.UUID;

// Inventory service responsible for reserving items for an order.
// It emits an event instead of directly updating Sales or Fulfillment.
public final class InventoryReservationService {

    private final StockRepository stockRepository;
    private final TransactionalOutboxPublisher eventPublisher;

    public InventoryReservationService(
            StockRepository stockRepository,
            TransactionalOutboxPublisher eventPublisher
    ) {
        this.stockRepository = stockRepository;
        this.eventPublisher = eventPublisher;
    }

    public void reserve(OrderPlacedEvent event, String correlationId) {
        boolean reserved = stockRepository.tryReserve(event.orderId(), event.items());

        if (reserved) {
            eventPublisher.publish(new InventoryReservedEvent(
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    "R-" + UUID.randomUUID(),
                    Instant.now()
            ), correlationId);
        } else {
            eventPublisher.publish(new InventoryReservationFailedEvent(
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    "Insufficient stock",
                    Instant.now()
            ), correlationId);
        }
    }
}