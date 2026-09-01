package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.cloudarchitecture.entity.ProcessedOrderEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.ProcessedOrderEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Worker for asynchronous order processing with durable deduplication.
 *
 * This could run as a separate Cloud Run service, Cloud Run job, or another
 * container subscribed to a Pub/Sub topic.
 */
@Service
public class OrderWorker {

    private final ProcessedOrderEventRepository processedEvents;
    private final OrderFulfillmentGateway fulfillmentGateway;
    private final Clock clock;

    public OrderWorker(
            ProcessedOrderEventRepository processedEvents,
            OrderFulfillmentGateway fulfillmentGateway
    ) {
        this(processedEvents, fulfillmentGateway, Clock.systemUTC());
    }

    public OrderWorker(
            ProcessedOrderEventRepository processedEvents,
            OrderFulfillmentGateway fulfillmentGateway,
            Clock clock
    ) {
        this.processedEvents = Objects.requireNonNull(processedEvents, "processedEvents must not be null");
        this.fulfillmentGateway = Objects.requireNonNull(
                fulfillmentGateway,
                "fulfillmentGateway must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Processes an order-created event.
     *
     * The method should be idempotent because the same message may be delivered
     * multiple times after retries or worker failures.
     */
    @Transactional
    public void processOrderCreated(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        if (processedEvents.existsById(orderId)) {
            return;
        }

        String idempotencyKey = "order-created:" + orderId;
        fulfillmentGateway.fulfill(orderId, idempotencyKey);
        processedEvents.save(new ProcessedOrderEventEntity(orderId, Instant.now(clock)));
    }
}
