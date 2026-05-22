package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.contracts;

import java.math.BigDecimal;

// Value payload used in integration contracts.
// It should be stable and versioned as part of the published language.
public record MoneyPayload(
        BigDecimal amount,
        String currency
) {
}