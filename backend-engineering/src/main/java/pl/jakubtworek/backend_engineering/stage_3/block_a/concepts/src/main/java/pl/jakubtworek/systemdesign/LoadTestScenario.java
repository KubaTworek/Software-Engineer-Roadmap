package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;
import java.util.List;

/**
 * Describes a load test scenario.
 *
 * A good test should compare observed bottlenecks against the expected model:
 * first bottleneck, next bottleneck, and the point where p95/p99 latency bends.
 */
public record LoadTestScenario(
        LoadTestType type,
        Duration duration,
        double targetRps,
        List<String> metricsToObserve,
        String expectedFailureMode
) {
    public LoadTestScenario {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (targetRps <= 0) {
            throw new IllegalArgumentException("targetRps must be positive");
        }
        if (metricsToObserve == null || metricsToObserve.isEmpty()) {
            throw new IllegalArgumentException("metricsToObserve must not be empty");
        }
        if (expectedFailureMode == null || expectedFailureMode.isBlank()) {
            throw new IllegalArgumentException("expectedFailureMode is required");
        }
    }
}
