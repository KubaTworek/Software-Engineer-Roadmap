package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;
import java.util.Set;

/**
 * Describes a test scenario that validates a specific design assumption.
 *
 * A good test does not merely generate traffic.
 * It confirms whether the predicted bottleneck or failure behavior appears in metrics.
 */
public record LoadTestScenario(
        LoadTestType type,
        Duration duration,
        String purpose,
        Set<TestMetric> metrics,
        String expectedObservation,
        String failureInterpretation
) {
    public LoadTestScenario {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        requireText(purpose, "purpose");
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("metrics must not be empty");
        }
        requireText(expectedObservation, "expectedObservation");
        requireText(failureInterpretation, "failureInterpretation");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
