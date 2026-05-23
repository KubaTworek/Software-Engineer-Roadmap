package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes a predicted bottleneck and the approximate RPS at which it appears.
 */
public record BottleneckPrediction(
        BottleneckType type,
        double limitRps,
        String explanation,
        String confirmingMetric
) {
    public BottleneckPrediction {
        if (type == null) {
            throw new IllegalArgumentException("Bottleneck type is required");
        }
        if (limitRps <= 0) {
            throw new IllegalArgumentException("limitRps must be positive");
        }
        if (explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("Explanation is required");
        }
        if (confirmingMetric == null || confirmingMetric.isBlank()) {
            throw new IllegalArgumentException("Confirming metric is required");
        }
    }
}
