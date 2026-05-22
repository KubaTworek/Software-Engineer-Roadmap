package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.order;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Stable item snapshot used inside OrderPlaced.
 *
 * productName was added later as an optional field, which keeps schema evolution safer.
 */
public record OrderPlacedItem(
        String productId,
        Optional<String> productName,
        int quantity,
        BigDecimal unitPrice
) {
}