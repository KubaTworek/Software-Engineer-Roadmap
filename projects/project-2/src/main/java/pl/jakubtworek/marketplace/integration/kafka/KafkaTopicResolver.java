package pl.jakubtworek.marketplace.integration.kafka;

public class KafkaTopicResolver {
    public KafkaTopic resolve(String eventType) {
        if (eventType.startsWith("Order")) return KafkaTopic.ORDER_EVENTS;
        if (eventType.startsWith("Payment")) return KafkaTopic.PAYMENT_EVENTS;
        if (eventType.startsWith("Stock")) return KafkaTopic.INVENTORY_EVENTS;
        return KafkaTopic.FULFILLMENT_EVENTS;
    }
}
