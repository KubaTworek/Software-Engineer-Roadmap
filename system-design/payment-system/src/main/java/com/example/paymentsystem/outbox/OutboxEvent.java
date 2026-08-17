package com.example.paymentsystem.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "aggregate_type")
    private String aggregateType;
    @Column(name = "aggregate_id")
    private UUID aggregateId;
    @Column(name = "event_type")
    private String eventType;
    @Lob
    @Column(name = "payload")
    private String payload;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.eventId = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }
}
