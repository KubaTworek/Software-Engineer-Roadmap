package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;

/**
 * Describes explicit timeout budgets for a remote call.
 *
 * Remote calls should not rely on accidental or infinite defaults.
 */
public record TimeoutBudget(
        Duration connectionTimeout,
        Duration requestTimeout,
        Duration totalRetryBudget
) {
    public TimeoutBudget {
        if (connectionTimeout == null || connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (totalRetryBudget == null || totalRetryBudget.isNegative() || totalRetryBudget.isZero()) {
            throw new IllegalArgumentException("totalRetryBudget must be positive");
        }
        if (totalRetryBudget.compareTo(requestTimeout) < 0) {
            throw new IllegalArgumentException("totalRetryBudget should be greater than or equal to requestTimeout");
        }
    }
}
