package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

// Domain event emitted when an order is successfully placed.
// It is produced by the aggregate and later published by the application service.
public record OrderPlacedEvent(
        String eventId,
        Instant occurredAt,
        OrderId orderId,
        CustomerId customerId,
        Money total
) implements DomainEvent {

    public static OrderPlacedEvent now(
            OrderId orderId,
            CustomerId customerId,
            Money total
    ) {
        return new OrderPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                orderId,
                customerId,
                total
        );
    }

    @Override
    public String eventType() {
        return "OrderPlaced";
    }
}