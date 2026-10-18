package com.example.filestorage.production.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Lokalny adapter demonstracyjny; nie deklaruje trwałości brokera. */
@Component
@ConditionalOnProperty(name = "app.production.events.adapter", havingValue = "logging", matchIfMissing = true)
public class LoggingDomainEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

    @Override
    public void publish(OutboxEnvelope event) {
        log.info("Publishing event id={} type={} aggregate={}:{}",
                event.id(), event.eventType(), event.aggregateType(), event.aggregateId());
    }
}
