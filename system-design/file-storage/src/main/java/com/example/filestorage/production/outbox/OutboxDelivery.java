package com.example.filestorage.production.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;
import com.example.filestorage.production.ops.FileStorageMetrics;

/** Zapisuje wynik publikacji w osobnej, krótkiej transakcji. */
@Service
public class OutboxDelivery {
    private final OutboxEventRepository events;
    private final DeadLetterEventRepository deadLetters;
    private final OutboxProperties properties;
    private final FileStorageMetrics metrics;

    public OutboxDelivery(OutboxEventRepository events, DeadLetterEventRepository deadLetters,
                          OutboxProperties properties, FileStorageMetrics metrics) {
        this.events = events;
        this.deadLetters = deadLetters;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public DomainEventPublisher.OutboxEnvelope envelope(UUID id) {
        OutboxEvent event = require(id);
        return new DomainEventPublisher.OutboxEnvelope(event.getId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId(), event.getPayload());
    }

    @Transactional
    public void succeeded(UUID id) {
        require(id).markPublished();
        metrics.outboxDelivery("published");
    }

    @Transactional
    public void failed(UUID id, String reason) {
        OutboxEvent event = require(id);
        if (event.getAttempts() >= properties.maxAttempts()) {
            event.markFailed(reason);
            deadLetters.save(new DeadLetterEvent(event, reason));
            metrics.outboxDelivery("dead_letter");
        } else {
            event.retryLater(reason);
            metrics.outboxDelivery("retry");
        }
    }

    private OutboxEvent require(UUID id) {
        return events.findById(id).orElseThrow(() -> new NoSuchElementException("Outbox event not found"));
    }
}
