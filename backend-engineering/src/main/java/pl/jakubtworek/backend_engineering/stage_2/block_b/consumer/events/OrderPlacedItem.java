package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.events;

import java.math.BigDecimal;

/**
 * Snapshot of a single order item included in the OrderPlaced event.
 *
 * Consumers should treat this data as a historical snapshot, not as a live product query.
 */
public record OrderPlacedItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}