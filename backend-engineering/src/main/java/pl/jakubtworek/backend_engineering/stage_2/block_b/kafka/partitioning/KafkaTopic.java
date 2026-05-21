package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.partitioning;

/**
 * Represents a Kafka topic used in the e-commerce system.
 *
 * Topics group events by business area or event stream.
 */
public enum KafkaTopic {

    /**
     * Topic containing order-related events.
     *
     * Example events: OrderPlaced, OrderCancelled.
     */
    ORDERS("orders"),

    /**
     * Topic containing payment-related events.
     *
     * Example events: PaymentAuthorized, PaymentFailed.
     */
    PAYMENTS("payments"),

    /**
     * Topic containing shipping-related events.
     *
     * Example events: ShippingInitiated, OrderShipped.
     */
    SHIPPING("shipping");

    private final String topicName;

    KafkaTopic(String topicName) {
        this.topicName = topicName;
    }

    /**
     * Returns the physical Kafka topic name.
     */
    public String topicName() {
        return topicName;
    }
}