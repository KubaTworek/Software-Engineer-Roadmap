package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity;

/**
 * Result of bottleneck analysis.
 */
public record Bottleneck(
        BottleneckType type,
        double limitRps,
        String reason,
        String metricsToConfirm
) {
    public Bottleneck {
        if (type == null) throw new IllegalArgumentException("type is required");
        if (Double.isNaN(limitRps) || limitRps < 0) throw new IllegalArgumentException("limitRps must not be NaN or negative");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        if (metricsToConfirm == null || metricsToConfirm.isBlank()) throw new IllegalArgumentException("metricsToConfirm are required");
    }

    public boolean appliesToPath() {
        return Double.isFinite(limitRps);
    }
}
