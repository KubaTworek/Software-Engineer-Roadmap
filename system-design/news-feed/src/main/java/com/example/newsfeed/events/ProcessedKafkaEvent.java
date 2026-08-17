package com.example.newsfeed.events;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_kafka_events")
public class ProcessedKafkaEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedKafkaEvent() {
    }

    public ProcessedKafkaEvent(UUID eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
