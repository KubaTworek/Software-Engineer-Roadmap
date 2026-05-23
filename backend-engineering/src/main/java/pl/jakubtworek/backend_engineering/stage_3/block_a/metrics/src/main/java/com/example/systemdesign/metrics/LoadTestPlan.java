package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.time.Duration;
import java.util.List;

/**
 * Describes a load test plan that validates a model instead of only producing charts.
 */
public record LoadTestPlan(
        LoadTestKind kind,
        Duration duration,
        String purpose,
        List<String> metrics,
        String expectedObservation
) {
    public LoadTestPlan {
        if (kind == null) {
            throw new IllegalArgumentException("Kind is required");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        requireText(purpose, "purpose");
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("Metrics must not be empty");
        }
        requireText(expectedObservation, "expectedObservation");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
