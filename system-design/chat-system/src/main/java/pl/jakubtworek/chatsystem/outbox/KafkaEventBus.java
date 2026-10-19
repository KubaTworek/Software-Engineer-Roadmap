package pl.jakubtworek.chatsystem.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Produkcyjna granica brokera. Kluczem jest aggregateId (dla wiadomości messageId);
 * deployment może wybrać conversationId jako aggregate, jeśli wymaga kolejności całej rozmowy.
 */
@Component
@Profile("kafka")
public class KafkaEventBus implements EventBus {
    private final KafkaTemplate<String, String> kafka; private final ObjectMapper json; private final String topic;
    public KafkaEventBus(KafkaTemplate<String, String> kafka, ObjectMapper json,
                         @Value("${app.outbox.kafka-topic:chat-domain-events}") String topic) {
        this.kafka = kafka; this.json = json; this.topic = topic;
    }
    @Override public void enqueue(OutboxEvent event) {
        try {
            String body = json.writeValueAsString(new KafkaEnvelope(event.getId(), event.getAggregateId(),
                    event.getEventType(), event.getPayloadJson(), Instant.now()));
            kafka.send(topic, partitionKey(event), body).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) { throw new IllegalStateException("Kafka publication failed", ex); }
    }
    private String partitionKey(OutboxEvent event) {
        try {
            var node = json.readTree(event.getPayloadJson()).get("conversationId");
            return node == null ? event.getAggregateId().toString() : node.asText();
        } catch (Exception ignored) { return event.getAggregateId().toString(); }
    }
    public record KafkaEnvelope(UUID eventId, UUID aggregateId, String eventType,
                                String payloadJson, Instant publishedAt) {}
}
