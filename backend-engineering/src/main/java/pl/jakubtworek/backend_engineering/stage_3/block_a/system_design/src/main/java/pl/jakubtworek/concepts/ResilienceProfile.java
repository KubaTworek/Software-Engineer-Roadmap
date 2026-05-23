package pl.jakubtworek.backend_engineering.stage_3.block_a.system_design.src.main.java.pl.jakubtworek.concepts;

import java.util.Set;

/**
 * Groups resilience mechanisms for one critical path.
 *
 * A critical path is healthy when failures are bounded, visible,
 * and intentionally degraded instead of spreading across the system.
 */
public record ResilienceProfile(
        TimeoutBudget timeoutBudget,
        RetryPolicy retryPolicy,
        CircuitBreaker circuitBreaker,
        Set<OperationalLever> emergencyLevers
) {
    public ResilienceProfile {
        if (timeoutBudget == null) {
            throw new IllegalArgumentException("timeoutBudget is required");
        }
        if (retryPolicy == null) {
            throw new IllegalArgumentException("retryPolicy is required");
        }
        if (circuitBreaker == null) {
            throw new IllegalArgumentException("circuitBreaker is required");
        }
        if (emergencyLevers == null || emergencyLevers.isEmpty()) {
            throw new IllegalArgumentException("At least one emergency lever is required");
        }
    }
}
