package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity;

/**
 * Result of bottleneck analysis.
 */
public record Bottleneck(
        BottleneckType type,
        double limitRps,
        String reason,
        String metricsToConfirm
) {
}