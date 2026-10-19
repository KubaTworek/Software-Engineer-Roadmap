package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local publishing adapter used by the laboratory.
 *
 * It logs the event instead of contacting GCP, so the module remains runnable
 * without cloud credentials. Replace it with a Google Cloud Pub/Sub adapter
 * behind the same method boundary for deployment exercises.
 */
@Service
public class PubSubPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubPublisher.class);
    /**
     * Publishes an order-created event.
     *
     * Consumers must be idempotent because Pub/Sub-style systems may deliver
     * messages more than once.
     */
    public void publishOrderCreated(Long orderId) {
        // Intentional local adapter: deployment code replaces this class with a
        // Pub/Sub SDK adapter while preserving the application-facing contract.
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive");
        }
        LOGGER.info("event=ORDER_CREATED orderId={}", orderId);
    }
}
