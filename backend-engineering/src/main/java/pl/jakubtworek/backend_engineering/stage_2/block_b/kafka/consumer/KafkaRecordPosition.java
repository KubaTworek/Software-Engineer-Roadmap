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
    /**
     * Kafka offset commits store the next offset to consume,
     * not the offset that has just been processed.
     */
    public long nextOffset() {
        return offset + 1;
    }
}