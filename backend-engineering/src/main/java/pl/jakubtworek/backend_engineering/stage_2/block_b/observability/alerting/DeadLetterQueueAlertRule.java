package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

import java.util.Optional;

/**
 * Alert rule for dead-letter queue growth.
 *
 * A growing DLQ usually means that some events cannot be processed.
 */
public class DeadLetterQueueAlertRule {

    private final long maxAllowedDlqSize;

    public DeadLetterQueueAlertRule(long maxAllowedDlqSize) {
        if (maxAllowedDlqSize < 0) {
            throw new IllegalArgumentException("Maximum DLQ size cannot be negative");
        }
        this.maxAllowedDlqSize = maxAllowedDlqSize;
    }

    /**
     * Evaluates current DLQ size.
     */
    public Optional<Alert> evaluate(String dlqTopic, long currentSize) {
        if (dlqTopic == null || dlqTopic.isBlank() || currentSize < 0) {
            throw new IllegalArgumentException("DLQ topic and non-negative size are required");
        }
        if (currentSize <= maxAllowedDlqSize) {
            return Optional.empty();
        }

        return Optional.of(new Alert(
                "DLQ size exceeded",
                AlertSeverity.CRITICAL,
                "DLQ topic " + dlqTopic + " contains " + currentSize + " messages."
        ));
    }
}
