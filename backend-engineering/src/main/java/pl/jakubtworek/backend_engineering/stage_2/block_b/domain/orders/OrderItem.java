package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.orders;

import java.math.BigDecimal;

/**
 * Single line item inside an order.
 *
 * The price is included here as a snapshot.
 * In event-driven systems, events should usually contain the data needed
 * by consumers instead of forcing them to query the producer service.
 */
public record OrderItem(
        String productId,
        int quantity,
        BigDecimal unitPrice
) {
}