package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.kafka;

import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.MetricNames;
import pl.jakubtworek.backend_engineering.stage_2.block_b.observability.metrics.MetricsRecorder;

import java.util.Map;

/**
 * Exports Kafka consumer lag as metrics.
 *
 * Consumer lag is one of the most important signals for event-driven systems,
 * because growing lag means consumers cannot keep up with producers.
 */
public class ConsumerLagMonitor {

    private final ConsumerLagProvider lagProvider;
    private final MetricsRecorder metricsRecorder;

    public ConsumerLagMonitor(
            ConsumerLagProvider lagProvider,
            MetricsRecorder metricsRecorder
    ) {
        this.lagProvider = lagProvider;
        this.metricsRecorder = metricsRecorder;
    }

    /**
     * Collects lag values and records them as gauges.
     */
    public void collect(String consumerGroup) {
        for (ConsumerLag lag : lagProvider.getLag(consumerGroup)) {
            metricsRecorder.recordGauge(
                    MetricNames.CONSUMER_LAG,
                    lag.lag(),
                    Map.of(
                            "consumerGroup", lag.consumerGroup(),
                            "topic", lag.topic(),
                            "partition", String.valueOf(lag.partition())
                    )
            );
        }
    }
}