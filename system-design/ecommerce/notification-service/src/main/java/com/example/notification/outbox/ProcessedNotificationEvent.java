package com.example.notification.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_processed_events")
public class ProcessedNotificationEvent {
    @Id
    private Long outboxEventId;
    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedNotificationEvent() {}
    public ProcessedNotificationEvent(Long outboxEventId) {
        this.outboxEventId = outboxEventId;
    }
}
