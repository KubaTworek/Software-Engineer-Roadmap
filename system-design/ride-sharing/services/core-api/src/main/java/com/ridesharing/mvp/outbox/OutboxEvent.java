package com.ridesharing.mvp.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String aggregateType;
    @Column(nullable = false)
    private UUID aggregateId;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private String topic;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;
    private int attempts;
    @Column(columnDefinition = "TEXT")
    private String lastError;
    private Instant createdAt;
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = OutboxStatus.PENDING;
    }
}
