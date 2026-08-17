package com.example.searchindexer.outbox;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "search_indexer_processed_events")
public class ProcessedSearchEvent {
    @Id
    private Long outboxEventId;
    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedSearchEvent() {}
    public ProcessedSearchEvent(Long outboxEventId) {
        this.outboxEventId = outboxEventId;
    }
}
