package pl.jakubtworek.marketplace.integration.kafka;

import java.util.List;

public interface KafkaMessageBroker extends KafkaMessagePublisher {
    List<KafkaRecord> poll(String topic, String consumerGroup, int maxRecords);
    void commit(String topic, String consumerGroup, long offset);
    long committedOffset(String topic, String consumerGroup);
    default long endOffset(String topic) { return -1L; }
}
