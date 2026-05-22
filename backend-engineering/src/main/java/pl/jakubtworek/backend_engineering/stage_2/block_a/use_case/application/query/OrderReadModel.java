package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.query;

import java.math.BigDecimal;
import java.time.Instant;

// Read model optimized for queries.
// It is separate from the write aggregate and may be denormalized.
public record OrderReadModel(
        String orderId,
        String customerId,
        String status,
        BigDecimal total,
        String currency,
        Instant placedAt
) {
}