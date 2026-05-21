package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning;

/**
 * Partitioning strategy based on orderId.
 *
 * This strategy guarantees that OrderPlaced, PaymentAuthorized,
 * PaymentFailed and ShippingInitiated for the same order are sent
 * to the same partition, as long as they use the same topic partitioning setup.
 */
public class OrderIdPartitioningStrategy implements PartitioningStrategy {

    /**
     * Resolves the message key from event.orderId().
     */
    @Override
    public MessageKey resolveKey(PartitionedEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("orderId must not be empty.");
        }

        return MessageKey.orderKey(event.orderId());
    }
}