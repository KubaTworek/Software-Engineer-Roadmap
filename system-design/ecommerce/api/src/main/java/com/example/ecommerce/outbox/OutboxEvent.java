package com.example.ecommerce.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_status_created", columnList = "status,createdAt"))
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String aggregateType;
    @Column(nullable = false) private String aggregateId;
    @Column(nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "text") private String payloadJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutboxEventStatus status = OutboxEventStatus.NEW;
    @Column(nullable = false) private int publishAttempts = 0;
    private String lastError;
    private Instant nextAttemptAt;
    private Instant claimedAt;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant publishedAt;
    protected OutboxEvent() {}
    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        this.aggregateType = aggregateType; this.aggregateId = aggregateId; this.eventType = eventType; this.payloadJson = payloadJson;
    }
    public Long getId(){ return id; }
    public String getAggregateType(){ return aggregateType; }
    public String getAggregateId(){ return aggregateId; }
    public String getEventType(){ return eventType; }
    public String getPayloadJson(){ return payloadJson; }
    public OutboxEventStatus getStatus(){ return status; }
    public int getPublishAttempts(){ return publishAttempts; }
    public Instant getNextAttemptAt(){ return nextAttemptAt; }
    public Instant getCreatedAt(){ return createdAt; }
    public Instant getClaimedAt(){ return claimedAt; }
    public void claim(Instant now){
        if (status != OutboxEventStatus.NEW
                && status != OutboxEventStatus.FAILED
                && status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Cannot claim outbox event in status " + status);
        }
        this.status = OutboxEventStatus.PROCESSING;
        this.claimedAt = now;
    }
    public void markPublished(){ this.status = OutboxEventStatus.PUBLISHED; this.publishedAt = Instant.now(); this.lastError = null; this.nextAttemptAt = null; this.claimedAt = null; }
    public void markFailed(String error){
        this.publishAttempts++;
        this.lastError = error;
        this.claimedAt = null;
        if (publishAttempts >= 5) {
            this.status = OutboxEventStatus.DEAD;
            this.nextAttemptAt = null;
        } else {
            this.status = OutboxEventStatus.FAILED;
            this.nextAttemptAt = Instant.now().plusSeconds(1L << publishAttempts);
        }
    }
}
