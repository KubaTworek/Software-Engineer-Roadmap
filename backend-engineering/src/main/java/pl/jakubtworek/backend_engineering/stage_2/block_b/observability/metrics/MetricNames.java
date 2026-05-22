package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics;

/**
 * Names of important metrics in an event-driven system.
 *
 * Stable metric names make dashboards and alerts easier to maintain.
 */
public final class MetricNames {

    public static final String CONSUMER_LAG = "kafka.consumer.lag";
    public static final String EVENTS_PROCESSED = "events.processed.total";
    public static final String EVENTS_FAILED = "events.failed.total";
    public static final String EVENTS_RETRIED = "events.retried.total";
    public static final String DLQ_MESSAGES = "events.dlq.total";
    public static final String OUTBOX_PENDING = "outbox.pending.count";
    public static final String PROCESSING_DURATION = "events.processing.duration.ms";

    private MetricNames() {
    }
}