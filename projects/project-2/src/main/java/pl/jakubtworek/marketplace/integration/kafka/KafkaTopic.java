package pl.jakubtworek.marketplace.integration.kafka;

public enum KafkaTopic {
    ORDER_EVENTS("marketplace.order-events.v1"),
    PAYMENT_EVENTS("marketplace.payment-events.v1"),
    INVENTORY_EVENTS("marketplace.inventory-events.v1"),
    FULFILLMENT_EVENTS("marketplace.fulfillment-events.v1"),
    DLQ("marketplace.dlq.v1");

    private final String topicName;

    KafkaTopic(String topicName) {
        this.topicName = topicName;
    }

    public String topicName() {
        return topicName;
    }
}
