package com.example.newsfeed.events;

import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventPublisher {

    private final KafkaTemplate<String, DomainEvent> kafkaTemplate;
    private final Counter publishedEvents;

    public KafkaEventPublisher(KafkaTemplate<String, DomainEvent> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishedEvents = Counter.builder("newsfeed.events.published")
                .description("Number of published domain events")
                .register(meterRegistry);
    }

    @Retry(name = "kafkaPublish")
    public void publish(String topic, String key, DomainEvent event) {
        kafkaTemplate.send(topic, key, event);
        publishedEvents.increment();
    }
}
