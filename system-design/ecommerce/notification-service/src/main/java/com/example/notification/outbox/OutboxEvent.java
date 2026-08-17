package com.example.notification.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    @Lob
    private String payloadJson;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
}
