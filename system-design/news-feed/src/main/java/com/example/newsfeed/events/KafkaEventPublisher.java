package com.example.newsfeed.events;

import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
        try {
            /*
             * Samo wywołanie send() oznacza jedynie umieszczenie rekordu w
             * buforze klienta. Outbox można oznaczyć jako wysłany dopiero po
             * potwierdzeniu brokera.
             */
            kafkaTemplate.send(topic, key, event).get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            publishedEvents.increment();
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka did not acknowledge outbox event", exception);
        }
    }
}
