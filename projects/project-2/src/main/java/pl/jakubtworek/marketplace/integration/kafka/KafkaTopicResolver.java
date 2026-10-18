package pl.jakubtworek.marketplace.integration.kafka;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Component
public class KafkaTopicResolver {
    private final String orderEvents;
    private final String paymentEvents;
    private final String inventoryEvents;
    private final String fulfillmentEvents;

    public KafkaTopicResolver() {
        this(KafkaTopic.ORDER_EVENTS.topicName(), KafkaTopic.PAYMENT_EVENTS.topicName(),
                KafkaTopic.INVENTORY_EVENTS.topicName(), KafkaTopic.FULFILLMENT_EVENTS.topicName());
    }

    /**
     * Nazwy topiców są konfiguracją środowiska, a nie stałą domenową. Wcześniej YAML
     * wyglądał na konfigurowalny, ale resolver ignorował go i zawsze używał enumów.
     * Jawny konstruktor sprawia, że test z losowymi topicami naprawdę izoluje run.
     */
    @Autowired
    public KafkaTopicResolver(
            @Value("${marketplace.kafka.topics.order-events:marketplace.order-events.v1}") String orderEvents,
            @Value("${marketplace.kafka.topics.payment-events:marketplace.payment-events.v1}") String paymentEvents,
            @Value("${marketplace.kafka.topics.inventory-events:marketplace.inventory-events.v1}") String inventoryEvents,
            @Value("${marketplace.kafka.topics.fulfillment-events:marketplace.fulfillment-events.v1}") String fulfillmentEvents) {
        this.orderEvents = requireTopic(orderEvents);
        this.paymentEvents = requireTopic(paymentEvents);
        this.inventoryEvents = requireTopic(inventoryEvents);
        this.fulfillmentEvents = requireTopic(fulfillmentEvents);
    }

    public ResolvedTopic resolve(String eventType) {
        if (eventType.startsWith("Order")) return new ResolvedTopic(orderEvents);
        if (eventType.startsWith("Payment")) return new ResolvedTopic(paymentEvents);
        if (eventType.startsWith("Stock")) return new ResolvedTopic(inventoryEvents);
        return new ResolvedTopic(fulfillmentEvents);
    }

    private String requireTopic(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Kafka topic cannot be blank");
        return value;
    }

    public record ResolvedTopic(String topicName) {}
}
