package com.example.filestorage.production.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * At-least-once relay. Crash po publish, lecz przed `succeeded`, może dać
 * duplikat, dlatego event ID jest stabilnym kluczem deduplikacji konsumenta.
 */
@Component
@ConditionalOnProperty(name = "app.production.workers.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherWorker {
    private final OutboxClaims claims;
    private final OutboxDelivery delivery;
    private final DomainEventPublisher publisher;

    public OutboxPublisherWorker(OutboxClaims claims, OutboxDelivery delivery,
                                 DomainEventPublisher publisher) {
        this.claims = claims;
        this.delivery = delivery;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.production.outbox.publish-fixed-delay-ms:5000}")
    public void publishPendingEvents() {
        for (var id : claims.claim(50)) {
            try {
                publisher.publish(delivery.envelope(id));
                delivery.succeeded(id);
            } catch (Exception exception) {
                String reason = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                delivery.failed(id, reason);
            }
        }
    }
}
