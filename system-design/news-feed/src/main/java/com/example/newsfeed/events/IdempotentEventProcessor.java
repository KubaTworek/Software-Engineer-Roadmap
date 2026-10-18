package com.example.newsfeed.events;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotentEventProcessor {

    private final ProcessedKafkaEventRepository repository;

    public IdempotentEventProcessor(ProcessedKafkaEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean processOnce(String consumerName, DomainEvent event, Runnable action) {
        /*
         * Jedno atomowe INSERT ... ON CONFLICT zastępuje podatne na race
         * exists-then-insert. Marker i efekty bazodanowe uczestniczą w tej
         * samej transakcji i wycofują się razem po wyjątku.
         */
        if (repository.claim(consumerName, event.eventId(), event.eventType()) == 0) {
            return false;
        }

        action.run();
        return true;
    }
}
