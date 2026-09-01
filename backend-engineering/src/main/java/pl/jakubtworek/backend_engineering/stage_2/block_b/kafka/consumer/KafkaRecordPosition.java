package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

/**
 * Represents the position of one consumed Kafka record.
 *
 * Kafka records are ordered by offset inside a single partition.
 */
public record KafkaRecordPosition(
        String topic,
        int partition,
        long offset
) {
    public KafkaRecordPosition {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Topic cannot be empty");
        }
        if (partition < 0 || offset < 0) {
            throw new IllegalArgumentException("Partition and offset cannot be negative");
        }
    }

    /**
     * Kafka offset commits store the next offset to consume,
     * not the offset that has just been processed.
     */
    public long nextOffset() {
        return Math.incrementExact(offset);
    }
}
