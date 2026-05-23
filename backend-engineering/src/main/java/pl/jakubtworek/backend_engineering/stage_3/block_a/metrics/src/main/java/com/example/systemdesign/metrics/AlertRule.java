package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.time.Duration;

/**
 * Represents a starting alert rule.
 *
 * Thresholds in system design are usually not universal constants.
 * They are initial heuristics that must be calibrated against the system's SLO,
 * baseline, traffic profile, and error budget.
 */
public record AlertRule(
        MetricDefinition metric,
        String condition,
        Duration sustainedFor,
        AlertSeverity severity,
        String interpretation
) {
    public AlertRule {
        if (metric == null) {
            throw new IllegalArgumentException("Metric is required");
        }
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("Condition is required");
        }
        if (sustainedFor == null || sustainedFor.isNegative() || sustainedFor.isZero()) {
            throw new IllegalArgumentException("Sustained duration must be positive");
        }
        if (severity == null) {
            throw new IllegalArgumentException("Severity is required");
        }
        if (interpretation == null || interpretation.isBlank()) {
            throw new IllegalArgumentException("Interpretation is required");
        }
    }
}
