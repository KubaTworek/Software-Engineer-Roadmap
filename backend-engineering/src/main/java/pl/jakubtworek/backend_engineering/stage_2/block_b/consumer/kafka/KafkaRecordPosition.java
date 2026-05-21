package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.kafka;

/**
 * Represents the position of a consumed Kafka record.
 *
 * Keeping topic, partition and offset together helps commit the correct offset
 * only after business side effects are completed.
 */
public record KafkaRecordPosition(
        String topic,
        int partition,
        long offset
) {
    /**
     * Kafka commits the next offset, not the currently processed offset.
     */
    public long nextOffset() {
        return offset + 1;
    }
}