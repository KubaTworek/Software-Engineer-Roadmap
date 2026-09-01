package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Component;

/** Boundary for slow downstream work triggered by an order-created event. */
@Component
public class OrderFulfillmentGateway {

    public void fulfill(Long orderId, String idempotencyKey) {
        // Call invoice, notification, or fulfillment services with the supplied key.
        // Those services must persist and deduplicate that key as well.
    }
}
