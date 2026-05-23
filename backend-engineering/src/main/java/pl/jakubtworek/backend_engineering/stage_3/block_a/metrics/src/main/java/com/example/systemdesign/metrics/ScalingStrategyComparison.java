package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes when a scaling strategy is appropriate and what trade-offs it brings.
 *
 * Scaling decisions are trade-offs, not universally correct answers.
 */
public record ScalingStrategyComparison(
        ScalingStrategy strategy,
        String whenToUse,
        String advantages,
        String costsAndRisks,
        String practicalRule
) {
    public ScalingStrategyComparison {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy is required");
        }
        requireText(whenToUse, "whenToUse");
        requireText(advantages, "advantages");
        requireText(costsAndRisks, "costsAndRisks");
        requireText(practicalRule, "practicalRule");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
