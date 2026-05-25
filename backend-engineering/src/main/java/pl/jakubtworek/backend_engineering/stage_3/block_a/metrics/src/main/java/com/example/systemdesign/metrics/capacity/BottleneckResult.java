package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity;

/**
 * Result of bottleneck prediction.
 */
public record BottleneckResult(
        BottleneckType type,
        double limitRps,
        String explanation,
        String confirmingMetrics
) {
}