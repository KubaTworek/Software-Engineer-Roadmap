package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.alerting;

import java.util.Optional;

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
        if (maxPendingEvents < 0 || maxOldestAgeSeconds < 0) {
            throw new IllegalArgumentException("Outbox alert thresholds cannot be negative");
        }
        this.maxPendingEvents = maxPendingEvents;
        this.maxOldestAgeSeconds = maxOldestAgeSeconds;
    }

    /**
     * Evaluates outbox health.
     */
    public Optional<Alert> evaluate(long pendingEvents, long oldestAgeSeconds) {
        if (pendingEvents < 0 || oldestAgeSeconds < 0) {
            throw new IllegalArgumentException("Outbox statistics cannot be negative");
        }

        if (oldestAgeSeconds > maxOldestAgeSeconds) {
            return Optional.of(new Alert(
                    "Outbox oldest event too old",
                    AlertSeverity.CRITICAL,
                    "Oldest unpublished outbox event is "
                            + oldestAgeSeconds
                            + " seconds old."
            ));
        }

        if (pendingEvents > maxPendingEvents) {
            return Optional.of(new Alert(
                    "Outbox pending events exceeded",
                    AlertSeverity.WARNING,
                    "Outbox contains " + pendingEvents + " unpublished events."
            ));
        }

        return Optional.empty();
    }
}
