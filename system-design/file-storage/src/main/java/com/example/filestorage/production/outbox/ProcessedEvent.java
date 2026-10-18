package com.example.filestorage.production.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Unikalny marker jest w tej samej bazie i transakcji co efekt konsumenta. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id private UUID eventId;
    @Column(name = "consumer_name", nullable = false) private String consumerName;
    @Column(name = "processed_at", nullable = false) private Instant processedAt;
    protected ProcessedEvent() {}
    public ProcessedEvent(UUID eventId, String consumerName, Instant processedAt) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.processedAt = processedAt;
    }
    public UUID getEventId() { return eventId; }
}
