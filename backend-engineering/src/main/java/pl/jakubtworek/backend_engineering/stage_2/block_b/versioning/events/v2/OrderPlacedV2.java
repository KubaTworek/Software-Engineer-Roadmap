package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.v2;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Version 2 of the OrderPlaced event.
 *
 * This version adds an optional customerId field.
 * Adding optional fields is usually compatible because older consumers can ignore them,
 * and newer consumers can handle older events where the field is missing.
 */
public record OrderPlacedV2(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String orderId,
        Optional<String> customerId,
        List<OrderPlacedItemV2> items,
        BigDecimal totalAmount
) {
}