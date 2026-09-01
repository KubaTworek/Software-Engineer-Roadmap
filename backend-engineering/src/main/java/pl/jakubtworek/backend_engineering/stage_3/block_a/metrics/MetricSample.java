package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

/**
 * Represents one measured value from monitoring.
 *
 * The value is intentionally generic.
 * Unit interpretation belongs to the alert rule.
 */
public record MetricSample(
        String name,
        double value
) {
    public MetricSample {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Metric name is required");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Metric value must be finite");
        }
    }
}
