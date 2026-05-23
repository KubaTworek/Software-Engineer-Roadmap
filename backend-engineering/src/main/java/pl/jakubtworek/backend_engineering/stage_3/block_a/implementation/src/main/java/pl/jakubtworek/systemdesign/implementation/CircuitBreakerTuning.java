package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;

/**
 * Describes starting parameters for a circuit breaker.
 *
 * These are not universal constants. They should be calibrated against traffic volume,
 * dependency behavior, SLO, and the cost of false positives.
 */
public record CircuitBreakerTuning(
        double failureRateThreshold,
        int minimumSampleSize,
        Duration openDuration,
        int halfOpenTrialCalls
) {
    public CircuitBreakerTuning {
        if (failureRateThreshold <= 0 || failureRateThreshold > 1) {
            throw new IllegalArgumentException("failureRateThreshold must be in range (0, 1]");
        }
        if (minimumSampleSize <= 0) {
            throw new IllegalArgumentException("minimumSampleSize must be positive");
        }
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        if (halfOpenTrialCalls <= 0) {
            throw new IllegalArgumentException("halfOpenTrialCalls must be positive");
        }
    }

    /**
     * Very low sample sizes make a breaker too sensitive to noise.
     */
    public boolean mayOpenTooEasily() {
        return minimumSampleSize < 10;
    }
}
