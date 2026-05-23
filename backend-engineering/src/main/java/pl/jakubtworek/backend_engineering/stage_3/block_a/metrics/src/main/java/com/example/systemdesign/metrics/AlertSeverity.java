package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Describes the operational severity of an alert.
 *
 * These levels are intentionally generic so they can be mapped to
 * any incident management system.
 */
public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
