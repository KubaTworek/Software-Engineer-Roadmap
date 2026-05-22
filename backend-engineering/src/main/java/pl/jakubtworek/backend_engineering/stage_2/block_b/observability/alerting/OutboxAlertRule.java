package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

/**
 * Alert rule for unpublished outbox events.
 *
 * If the outbox grows for too long, events may not be reaching Kafka.
 */
public class OutboxAlertRule {

    private final long maxPendingEvents;
    private final long maxOldestAgeSeconds;

    public OutboxAlertRule(
            long maxPendingEvents,
            long maxOldestAgeSeconds
    ) {
        this.maxPendingEvents = maxPendingEvents;
        this.maxOldestAgeSeconds = maxOldestAgeSeconds;
    }

    /**
     * Evaluates outbox health.
     */
    public Alert evaluate(long pendingEvents, long oldestAgeSeconds) {
        if (pendingEvents > maxPendingEvents) {
            return new Alert(
                    "Outbox pending events exceeded",
                    AlertSeverity.WARNING,
                    "Outbox contains " + pendingEvents + " unpublished events."
            );
        }

        if (oldestAgeSeconds > maxOldestAgeSeconds) {
            return new Alert(
                    "Outbox oldest event too old",
                    AlertSeverity.CRITICAL,
                    "Oldest unpublished outbox event is "
                            + oldestAgeSeconds
                            + " seconds old."
            );
        }

        return null;
    }
}