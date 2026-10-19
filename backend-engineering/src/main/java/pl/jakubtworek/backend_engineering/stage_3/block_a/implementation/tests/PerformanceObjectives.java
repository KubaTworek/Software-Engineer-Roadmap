package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;

/** SLO and saturation limits evaluated after every phase. */
public record PerformanceObjectives(
        Duration maxP95,
        Duration maxP99,
        double maxErrorRate,
        double maxSaturation,
        double minThroughputRatio) {

    public PerformanceObjectives {
        if (maxP95 == null || maxP95.isNegative() || maxP95.isZero()) {
            throw new IllegalArgumentException("maxP95 must be positive");
        }
        if (maxP99 == null || maxP99.compareTo(maxP95) < 0) {
            throw new IllegalArgumentException("maxP99 must not be lower than maxP95");
        }
        requireRate(maxErrorRate, "maxErrorRate");
        requireRate(maxSaturation, "maxSaturation");
        if (!Double.isFinite(minThroughputRatio) || minThroughputRatio <= 0 || minThroughputRatio > 1) {
            throw new IllegalArgumentException("minThroughputRatio must be in range (0, 1]");
        }
    }

    private static void requireRate(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be in range [0, 1]");
        }
    }
}
