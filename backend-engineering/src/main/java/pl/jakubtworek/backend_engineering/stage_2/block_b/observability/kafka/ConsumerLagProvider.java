package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.kafka;

import java.util.List;

/**
 * Provides consumer lag information.
 *
 * A production implementation may read this data from Kafka AdminClient,
 * JMX metrics, Burrow, Confluent tooling or Prometheus exporters.
 */
public interface ConsumerLagProvider {

    /**
     * Returns lag values for the selected consumer group.
     */
    List<ConsumerLag> getLag(String consumerGroup);
}