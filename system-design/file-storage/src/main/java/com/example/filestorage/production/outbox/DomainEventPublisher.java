package com.example.filestorage.production.outbox;

/** Production boundary: adapter do Kafki, SQS, Pub/Sub albo RabbitMQ. */
public interface DomainEventPublisher {
    void publish(OutboxEnvelope event);

    record OutboxEnvelope(java.util.UUID id, String eventType, String aggregateType,
                          java.util.UUID aggregateId, String payload) {}
}
