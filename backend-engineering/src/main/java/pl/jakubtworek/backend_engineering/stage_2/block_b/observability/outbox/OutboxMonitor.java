package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.outbox;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.MetricNames;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.MetricsRecorder;

import java.util.Map;

/**
 * Exports outbox health metrics.
 *
 * A growing outbox is often an early warning sign of broker problems
 * or a broken publisher process.
 */
public class OutboxMonitor {

    private final OutboxStatsProvider statsProvider;
    private final MetricsRecorder metricsRecorder;

    public OutboxMonitor(
            OutboxStatsProvider statsProvider,
            MetricsRecorder metricsRecorder
    ) {
        this.statsProvider = statsProvider;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Records current outbox metrics.
     */
    public void collect() {
        metricsRecorder.recordGauge(
                MetricNames.OUTBOX_PENDING,
                statsProvider.pendingEvents(),
                Map.of()
        );

        metricsRecorder.recordGauge(
                "outbox.oldest.pending.age.seconds",
                statsProvider.oldestPendingEventAgeSeconds(),
                Map.of()
        );
    }
}