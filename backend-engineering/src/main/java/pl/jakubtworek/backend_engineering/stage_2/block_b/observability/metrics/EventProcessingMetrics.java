package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.context.ObservabilityContext;

import java.util.Map;

/**
 * Records event processing metrics.
 *
 * These metrics are useful for understanding throughput, failures,
 * retries and processing latency.
 */
public class EventProcessingMetrics {

    private final MetricsRecorder metricsRecorder;

    public EventProcessingMetrics(MetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Records successful event processing.
     */
    public void recordProcessed(ObservabilityContext context, long durationMillis) {
        Map<String, String> tags = baseTags(context);

        metricsRecorder.incrementCounter(MetricNames.EVENTS_PROCESSED, tags);
        metricsRecorder.recordDuration(MetricNames.PROCESSING_DURATION, durationMillis, tags);
    }

    /**
     * Records failed event processing.
     */
    public void recordFailed(ObservabilityContext context) {
        metricsRecorder.incrementCounter(MetricNames.EVENTS_FAILED, baseTags(context));
    }

    /**
     * Records a retry attempt.
     */
    public void recordRetry(ObservabilityContext context, int attemptNumber) {
        Map<String, String> tags = baseTags(context);
        tags.put("attempt", String.valueOf(attemptNumber));

        metricsRecorder.incrementCounter(MetricNames.EVENTS_RETRIED, tags);
    }

    /**
     * Records that an event was moved to DLQ.
     */
    public void recordDlq(ObservabilityContext context) {
        metricsRecorder.incrementCounter(MetricNames.DLQ_MESSAGES, baseTags(context));
    }

    /**
     * Creates standard metric tags for event processing.
     */
    private Map<String, String> baseTags(ObservabilityContext context) {
        return new java.util.HashMap<>(Map.of(
                "eventType", context.eventType(),
                "sourceService", context.sourceService(),
                "aggregateId", context.aggregateId()
        ));
    }
}