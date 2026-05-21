package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer;

import java.math.BigDecimal;

/**
 * Item snapshot included in the OrderPlaced event.
 *
 * This is not necessarily the same class as the internal OrderItem entity.
 * Event payloads should be designed as stable public contracts.
 */
public record OrderPlacedItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}