package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.ordering;

import pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning.MessageKey;

/**
 * Describes why a stable message key is required.
 *
 * The same key should consistently map related events to the same partition.
 */
public class OrderingKeyPolicy {

    /**
     * Validates that a message key is suitable for ordered processing.
     */
    public void validate(MessageKey key) {
        if (key == null || key.value() == null || key.value().isBlank()) {
            throw new IllegalArgumentException(
                    "Kafka message key must be present to preserve per-order ordering."
            );
        }
    }
}