package com.example.filestorage.production.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Produkcyjny adapter brokera: ack `all` musi nadejsc przed oznaczeniem outboxa. */
@Component
@ConditionalOnProperty(name = "app.production.events.adapter", havingValue = "kafka")
public class KafkaDomainEventPublisher implements DomainEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    public KafkaDomainEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json,
                                     @Value("${app.production.events.topic}") String topic) {
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
    }

    @Override
    public void publish(OutboxEnvelope event) {
        try {
            String key = event.aggregateType() + ":" + event.aggregateId();
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json.writeValueAsString(event));
            record.headers().add("eventId", event.id().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", event.eventType().getBytes(StandardCharsets.UTF_8));
            kafka.send(record).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka did not acknowledge outbox event", exception);
        }
    }
}
