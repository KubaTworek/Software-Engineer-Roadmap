package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning;

/**
 * Represents a Kafka message key.
 *
 * In this system, orderId is used as the message key to guarantee that
 * all events related to the same order are written to the same partition.
 */
public record MessageKey(
        String value
) {
    public MessageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Kafka message key cannot be empty");
        }
    }

    /**
     * Creates a message key based on orderId.
     *
     * Using orderId as the key preserves ordering for all events belonging
     * to the same order workflow.
     */
    public static MessageKey orderKey(String orderId) {
        return new MessageKey(orderId);
    }
}
