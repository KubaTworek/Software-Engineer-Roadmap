package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

/**
 * Represents business and operational priority.
 *
 * Priority should be stable enough to drive escalation policies.
 */
public enum AlertPriority {

    P1("p1"),
    P2("p2"),
    P3("p3"),
    P4("p4");

    private final String labelValue;

    AlertPriority(String labelValue) {
        this.labelValue = labelValue;
    }

    public String labelValue() {
        return labelValue;
    }
}