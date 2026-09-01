package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

/**
 * Result of alert evaluation.
 *
 * The explanation should tell the operator what the alert means,
 * not only that a number crossed a threshold.
 */
public record AlertResult(
        String alertName,
        AlertSeverity severity,
        boolean firing,
        String explanation
) {
}