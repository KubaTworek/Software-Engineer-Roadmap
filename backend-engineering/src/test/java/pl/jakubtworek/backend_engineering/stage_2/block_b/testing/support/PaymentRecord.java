package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support;

import java.math.BigDecimal;

/**
 * Simplified payment database record used in tests.
 */
public record PaymentRecord(
        String orderId,
        BigDecimal amount,
        String status
) {
}