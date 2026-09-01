package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;

/** One observation window exported by a real load generator and telemetry backend. */
public record ExperimentObservation(
        String phase,
        int offeredRps,
        double achievedRps,
        Duration p95,
        Duration p99,
        double errorRate,
        double applicationSaturation,
        double dependencySaturation,
        long queueDepth,
        double loadShedRate,
        int replicas) {

    public ExperimentObservation {
        if (phase == null || phase.isBlank()) throw new IllegalArgumentException("phase is required");
        if (offeredRps <= 0) throw new IllegalArgumentException("offeredRps must be positive");
        if (!Double.isFinite(achievedRps) || achievedRps < 0) throw new IllegalArgumentException("achievedRps must be finite and non-negative");
        if (p95 == null || p95.isNegative()) throw new IllegalArgumentException("p95 must be non-negative");
        if (p99 == null || p99.compareTo(p95) < 0) throw new IllegalArgumentException("p99 must not be lower than p95");
        requireRate(errorRate, "errorRate");
        requireRate(applicationSaturation, "applicationSaturation");
        requireRate(dependencySaturation, "dependencySaturation");
        requireRate(loadShedRate, "loadShedRate");
        if (queueDepth < 0) throw new IllegalArgumentException("queueDepth must be non-negative");
        if (replicas <= 0) throw new IllegalArgumentException("replicas must be positive");
    }

    public double throughputRatio() {
        return achievedRps / offeredRps;
    }

    private static void requireRate(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be in range [0, 1]");
        }
    }
}
