package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;

/**
 * Abstraction for publishing messages to Google Cloud Pub/Sub.
 *
 * In a real implementation this class would use the Google Cloud Pub/Sub SDK.
 * Keeping it behind a service abstraction makes local testing easier.
 */
@Service
public class PubSubPublisher {
    /**
     * Publishes an order-created event.
     *
     * Consumers must be idempotent because Pub/Sub-style systems may deliver
     * messages more than once.
     */
    public void publishOrderCreated(Long orderId) {
        // Production example: create a PubsubMessage and publish it with the Pub/Sub SDK.
        System.out.println("{"severity":"INFO","event":"ORDER_CREATED","orderId":" + orderId + "}");
    }
}
