package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

/**
 * Represents a consumed Kafka record after deserialization.
 *
 * The key should usually be the same business key that was used by the producer,
 * for example orderId.
 */
public record ConsumedKafkaRecord<T>(
        String key,
        T value,
        KafkaRecordPosition position
) {
}