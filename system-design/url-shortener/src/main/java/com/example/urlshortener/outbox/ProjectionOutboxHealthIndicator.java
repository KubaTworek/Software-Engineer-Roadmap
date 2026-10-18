package com.example.urlshortener.outbox;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Pending depth is exposed without high-cardinality event identifiers. */
@Component
public class ProjectionOutboxHealthIndicator implements HealthIndicator {
    private final UrlProjectionOutboxRepository repository;

    public ProjectionOutboxHealthIndicator(UrlProjectionOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        long pending = repository.countBySentAtIsNullAndDeadLetteredAtIsNull();
        long deadLetters = repository.countByDeadLetteredAtIsNotNull();
        Health.Builder status = deadLetters == 0 ? Health.up() : Health.down();
        return status.withDetail("pending", pending).withDetail("deadLetters", deadLetters).build();
    }
}
