package com.example.newsfeed.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotentEventProcessor {

    private final ProcessedKafkaEventRepository repository;

    public IdempotentEventProcessor(ProcessedKafkaEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean processOnce(DomainEvent event, Runnable action) {
        if (repository.existsById(event.eventId())) {
            return false;
        }

        action.run();
        repository.save(new ProcessedKafkaEvent(event.eventId(), event.eventType(), Instant.now()));
        return true;
    }
}
