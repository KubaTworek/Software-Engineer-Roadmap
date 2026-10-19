package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;

/**
 * Defines executable parameters for a load test scenario.
 *
 * This is intentionally generic so it can be adapted to Gatling, k6, JMeter,
 * Locust, or a custom test runner.
 */
public record LoadTestScenario(
        LoadTestType type,
        Duration duration,
        int startRps,
        int targetRps,
        String expectedObservation
) {
    public LoadTestScenario {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }

        if (duration.toSeconds() < 1) {
            throw new IllegalArgumentException("duration must be at least one second");
        }

        if (startRps < 0) {
            throw new IllegalArgumentException("startRps must be non-negative");
        }

        if (targetRps <= 0) {
            throw new IllegalArgumentException("targetRps must be positive");
        }

        if ((type == LoadTestType.STEP || type == LoadTestType.STRESS || type == LoadTestType.SPIKE)
                && startRps > targetRps) {
            throw new IllegalArgumentException("startRps cannot exceed targetRps for ramp scenarios");
        }

        if (type == LoadTestType.SPIKE && duration.toSeconds() < 2) {
            throw new IllegalArgumentException("spike duration must be at least two seconds");
        }

        if (expectedObservation == null || expectedObservation.isBlank()) {
            throw new IllegalArgumentException("expectedObservation is required");
        }
    }
}
