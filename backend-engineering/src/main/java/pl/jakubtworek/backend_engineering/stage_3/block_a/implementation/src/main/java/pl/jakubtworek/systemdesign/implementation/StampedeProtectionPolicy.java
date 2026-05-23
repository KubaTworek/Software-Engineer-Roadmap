package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;
import java.util.Set;

/**
 * Describes how a system protects hot cache keys from stampede.
 *
 * Cache stampede happens when many requests miss the same hot key at once
 * and overload the source of truth.
 */
public record StampedeProtectionPolicy(
        Set<StampedeProtectionStrategy> strategies,
        Duration staleWindow,
        Duration maxJitter
) {
    public StampedeProtectionPolicy {
        if (strategies == null || strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one strategy is required");
        }
        if (staleWindow == null || staleWindow.isNegative()) {
            throw new IllegalArgumentException("staleWindow must be non-negative");
        }
        if (maxJitter == null || maxJitter.isNegative()) {
            throw new IllegalArgumentException("maxJitter must be non-negative");
        }
    }

    /**
     * Returns true when the policy has at least one mechanism that reduces synchronized refreshes.
     */
    public boolean reducesSynchronizedRefreshes() {
        return strategies.contains(StampedeProtectionStrategy.SINGLE_FLIGHT)
                || strategies.contains(StampedeProtectionStrategy.REQUEST_COALESCING)
                || strategies.contains(StampedeProtectionStrategy.TTL_JITTER)
                || strategies.contains(StampedeProtectionStrategy.STALE_WHILE_REVALIDATE);
    }
}
