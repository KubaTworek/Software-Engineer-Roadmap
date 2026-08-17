package com.example.newsfeed.events;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent {

    @Id
    private UUID id;

    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    protected DeadLetterEvent() {
    }

    public DeadLetterEvent(UUID id, UUID eventId, String eventType, String topic, String payload, String errorMessage, int attempts, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.attempts = attempts;
        this.createdAt = createdAt;
    }
}
