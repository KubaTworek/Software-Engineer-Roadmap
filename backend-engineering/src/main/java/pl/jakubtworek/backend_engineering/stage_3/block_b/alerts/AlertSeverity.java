package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

/**
 * Represents the operational severity of an alert.
 *
 * Severity is used for routing and responder expectations.
 * It should describe the required operational response, not just technical importance.
 */
public enum AlertSeverity {

    PAGE("page"),
    TICKET("ticket"),
    WARNING("warning"),
    INFO("info");

    private final String labelValue;

    AlertSeverity(String labelValue) {
        this.labelValue = labelValue;
    }

    public String labelValue() {
        return labelValue;
    }
}