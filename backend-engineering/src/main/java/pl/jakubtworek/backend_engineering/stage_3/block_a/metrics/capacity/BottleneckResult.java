package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity;

/**
 * Result of bottleneck prediction.
 */
public record BottleneckResult(
        BottleneckType type,
        double limitRps,
        String explanation,
        String confirmingMetrics
) {
    public BottleneckResult {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (Double.isNaN(limitRps) || limitRps < 0) throw new IllegalArgumentException("limitRps must not be NaN or negative");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("explanation is required");
        if (confirmingMetrics == null || confirmingMetrics.isBlank()) throw new IllegalArgumentException("confirmingMetrics are required");
    }

    /** A component unused by the modeled request path has no finite limit here. */
    public boolean appliesToPath() {
        return Double.isFinite(limitRps);
    }
}
