package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.v1;

import java.math.BigDecimal;

/**
 * Version 1 of an order item snapshot.
 */
public record OrderPlacedItemV1(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}