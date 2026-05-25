package pl.jakubtworek.backend_engineering.stage_3.block_b.structured_logs;

/**
 * Represents a normalized severity level for structured logs.
 *
 * severityNumber loosely follows the OpenTelemetry severity number model.
 * The exact mapping can be adjusted to your organization's logging policy.
 */
public enum LogSeverity {

    TRACE("TRACE", 1),
    DEBUG("DEBUG", 5),
    INFO("INFO", 9),
    WARN("WARN", 13),
    ERROR("ERROR", 17),
    FATAL("FATAL", 21);

    private final String severityText;
    private final int severityNumber;

    LogSeverity(String severityText, int severityNumber) {
        this.severityText = severityText;
        this.severityNumber = severityNumber;
    }

    public String severityText() {
        return severityText;
    }

    public int severityNumber() {
        return severityNumber;
    }
}