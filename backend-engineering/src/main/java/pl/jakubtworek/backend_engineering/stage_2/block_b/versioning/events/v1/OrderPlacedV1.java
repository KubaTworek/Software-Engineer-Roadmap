package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Version 1 of the OrderPlaced event.
 *
 * This is the initial public contract emitted when an order is created.
 */
public record OrderPlacedV1(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        List<OrderPlacedItemV1> items,
        BigDecimal totalAmount
) {
}