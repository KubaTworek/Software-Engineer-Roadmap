package com.example.paymentsystem.outbox;

/** Broker boundary. Delivery is at-least-once, so consumers must deduplicate eventId. */
public interface EventPublisher {
    void publish(OutboxEvent event);
}
