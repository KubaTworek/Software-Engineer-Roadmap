package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience;

import java.time.Duration;

/**
 * Explicit timeout configuration for a remote call.
 */
public record TimeoutConfig(
        Duration connectionTimeout,
        Duration requestTimeout
) {
    public TimeoutConfig {
        if (connectionTimeout == null || connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }

        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
    }
}