package com.example.newsfeed.events;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent {

    @EmbeddedId
    private ProcessedKafkaEventId id;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedKafkaEvent() {
    }

    public ProcessedKafkaEvent(String consumerName, UUID eventId, String eventType, Instant processedAt) {
        this.id = new ProcessedKafkaEventId(consumerName, eventId);
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
