package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

/**
 * Represents an alert produced by monitoring rules.
 */
public record Alert(
        String name,
        AlertSeverity severity,
        String message
) {
}