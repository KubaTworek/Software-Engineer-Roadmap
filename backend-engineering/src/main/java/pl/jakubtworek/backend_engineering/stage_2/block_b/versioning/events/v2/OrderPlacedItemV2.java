package pl.jakubtworek.backend_engineering.stage_2.block_b.versioning.events.v2;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Version 2 of an order item snapshot.
 *
 * This version adds an optional productName field.
 * The field is optional because older events will not contain it.
 */
public record OrderPlacedItemV2(
        String productId,
        Optional<String> productName,
        int quantity,
        BigDecimal unitPrice
) {
}