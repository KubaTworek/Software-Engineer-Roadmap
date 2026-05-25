package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;

/**
 * Conceptual worker for asynchronous order processing.
 *
 * This could run as a separate Cloud Run service, Cloud Run job, or another
 * container subscribed to a Pub/Sub topic.
 */
@Service
public class OrderWorker {
    /**
     * Processes an order-created event.
     *
     * The method should be idempotent because the same message may be delivered
     * multiple times after retries or worker failures.
     */
    public void processOrderCreated(Long orderId) {
        // Load order from database.
        // Check whether this order has already been processed.
        // Perform slow operations such as invoice generation or email delivery.
        // Mark processing status in durable storage.
    }
}
