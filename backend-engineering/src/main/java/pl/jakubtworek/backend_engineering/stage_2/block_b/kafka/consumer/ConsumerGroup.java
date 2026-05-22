package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.consumer;

/**
 * Represents a Kafka consumer group.
 *
 * Consumers with the same groupId share partitions between themselves.
 * Consumers with different groupIds receive independent copies of the stream.
 */
public record ConsumerGroup(
        String groupId
) {
    /**
     * Creates a consumer group for Payment Service.
     */
    public static ConsumerGroup paymentService() {
        return new ConsumerGroup("payment-service");
    }

    /**
     * Creates a consumer group for Shipping Service.
     */
    public static ConsumerGroup shippingService() {
        return new ConsumerGroup("shipping-service");
    }
}