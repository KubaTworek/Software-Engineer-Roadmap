package com.example.paymentsystem.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Claims a small batch under a database lock. Publishing and marking cannot be
 * atomic together, therefore a crash may publish twice; eventId-based consumer
 * idempotency is part of the contract, not an optional optimization.
 */
@Service
public class OutboxRelay {
    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final MeterRegistry metrics;

    public OutboxRelay(OutboxEventRepository repository, EventPublisher publisher, MeterRegistry metrics) {
        this.repository = repository;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public int publishBatch() {
        int published = 0;
        for (OutboxEvent event : repository
                .lockPublishableBatch(Instant.now())) {
            try {
                publisher.publish(event);
                event.published();
                metrics.counter("payment.outbox.published", "type", event.getEventType()).increment();
                published++;
            } catch (RuntimeException failure) {
                event.failed(failure);
                metrics.counter("payment.outbox.failures", "type", event.getEventType()).increment();
                if (event.isDeadLettered()) {
                    // Ta metryka jest sygnałem do manual review. Automatyczny
                    // replay DLQ bez usunięcia przyczyny może powielić efekt.
                    metrics.counter("payment.outbox.dead_lettered", "type", event.getEventType()).increment();
                }
            }
        }
        return published;
    }
}
