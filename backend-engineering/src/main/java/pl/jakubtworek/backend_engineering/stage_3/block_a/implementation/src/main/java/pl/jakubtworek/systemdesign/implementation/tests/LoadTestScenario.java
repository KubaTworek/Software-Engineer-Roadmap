package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.tests;

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

        if (startRps < 0) {
            throw new IllegalArgumentException("startRps must be non-negative");
        }

        if (targetRps <= 0) {
            throw new IllegalArgumentException("targetRps must be positive");
        }

        if (expectedObservation == null || expectedObservation.isBlank()) {
            throw new IllegalArgumentException("expectedObservation is required");
        }
    }
}