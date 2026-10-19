package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.scaling;

/**
 * Result of scaling strategy decision.
 */
public record ScalingDecision(
        ScalingStrategy strategy,
        String explanation,
        String mainRisk
) {
}