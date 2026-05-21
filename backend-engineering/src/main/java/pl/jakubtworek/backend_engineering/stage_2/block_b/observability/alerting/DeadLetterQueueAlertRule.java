package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

/**
 * Alert rule for dead-letter queue growth.
 *
 * A growing DLQ usually means that some events cannot be processed.
 */
public class DeadLetterQueueAlertRule {

    private final long maxAllowedDlqSize;

    public DeadLetterQueueAlertRule(long maxAllowedDlqSize) {
        this.maxAllowedDlqSize = maxAllowedDlqSize;
    }

    /**
     * Evaluates current DLQ size.
     */
    public Alert evaluate(String dlqTopic, long currentSize) {
        if (currentSize <= maxAllowedDlqSize) {
            return null;
        }

        return new Alert(
                "DLQ size exceeded",
                AlertSeverity.CRITICAL,
                "DLQ topic " + dlqTopic + " contains " + currentSize + " messages."
        );
    }
}