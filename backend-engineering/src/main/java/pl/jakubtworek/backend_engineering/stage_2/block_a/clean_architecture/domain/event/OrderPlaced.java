package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.event;

import pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model.OrderId;

import java.time.Instant;

// Domain event.
// It represents a business fact and does not know how it will be published.
public record OrderPlaced(
        String eventId,
        OrderId orderId,
        Instant occurredAt
) {
}