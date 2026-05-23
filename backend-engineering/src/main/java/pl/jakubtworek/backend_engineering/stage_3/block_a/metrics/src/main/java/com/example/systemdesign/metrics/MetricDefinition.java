package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes a single metric that should be collected and interpreted.
 *
 * A metric is useful only when it is connected to an operational decision.
 * For example, "pool wait" is useful because it can indicate dependency saturation,
 * not merely because it is easy to graph.
 */
public record MetricDefinition(
        String name,
        MetricCategory category,
        String meaning,
        String recommendedAggregation
) {
    public MetricDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Metric name is required");
        }
        if (category == null) {
            throw new IllegalArgumentException("Metric category is required");
        }
        if (meaning == null || meaning.isBlank()) {
            throw new IllegalArgumentException("Metric meaning is required");
        }
        if (recommendedAggregation == null || recommendedAggregation.isBlank()) {
            throw new IllegalArgumentException("Recommended aggregation is required");
        }
    }
}
