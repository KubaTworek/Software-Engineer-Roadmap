package pl.jakubtworek.backend_engineering.stage_2.block_b.observability.kafka;

/**
 * Represents consumer lag for one Kafka topic partition.
 *
 * Lag is the difference between the latest available offset and the
 * currently committed consumer offset.
 */
public record ConsumerLag(
        String consumerGroup,
        String topic,
        int partition,
        long currentOffset,
        long endOffset
) {
    /**
     * Calculates how many records are waiting to be processed.
     */
    public long lag() {
        return Math.max(0, endOffset - currentOffset);
    }
}