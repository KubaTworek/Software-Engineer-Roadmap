 package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote;

import java.time.Duration;

/**
 * Explicit timeout configuration for a remote dependency.
 *
 * Remote calls should not rely on accidental library defaults.
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