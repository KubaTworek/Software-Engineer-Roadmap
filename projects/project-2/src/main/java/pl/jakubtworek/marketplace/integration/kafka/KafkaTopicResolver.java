package pl.jakubtworek.marketplace.integration.kafka;

import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    public KafkaTopicResolver() {
    }

    public KafkaTopic resolve(String eventType) {
        if (eventType.startsWith("Order")) return KafkaTopic.ORDER_EVENTS;
        if (eventType.startsWith("Payment")) return KafkaTopic.PAYMENT_EVENTS;
        if (eventType.startsWith("Stock")) return KafkaTopic.INVENTORY_EVENTS;
        return KafkaTopic.FULFILLMENT_EVENTS;
    }
}
