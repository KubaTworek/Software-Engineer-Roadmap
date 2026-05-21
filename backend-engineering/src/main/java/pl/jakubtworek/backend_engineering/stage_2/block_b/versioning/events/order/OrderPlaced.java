package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable OrderPlaced event contract evolved in a compatible way.
 *
 * The event name remains the same across schema versions.
 * New fields should be optional or have defaults to preserve compatibility.
 */
public record OrderPlaced(
        UUID eventId,
        Instant occurredAt,
        int version,
        String correlationId,
        String orderId,

        /**
         * Optional field added after the initial schema version.
         *
         * Older messages may not contain this value, so consumers must handle absence.
         */
        Optional<String> customerId,

        List<OrderPlacedItem> items,
        BigDecimal totalAmount
) {
    /**
     * Current schema version used by the producer.
     */
    public static final int CURRENT_VERSION = 2;
}