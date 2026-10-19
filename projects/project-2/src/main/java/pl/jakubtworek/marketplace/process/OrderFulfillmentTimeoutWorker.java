package pl.jakubtworek.marketplace.process;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Oddziela timeout biznesowy sagi od timeoutu poll/request infrastruktury. */
@Component
public class OrderFulfillmentTimeoutWorker {
    private final OrderFulfillmentProcessManager manager;
    private final boolean enabled;

    public OrderFulfillmentTimeoutWorker(
            OrderFulfillmentProcessManager manager,
            @Value("${marketplace.fulfillment-process.timeout-worker-enabled:true}") boolean enabled
    ) {
        this.manager = manager;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${marketplace.fulfillment-process.timeout-worker-delay-ms:10000}")
    public void expireOverdue() {
        if (enabled) manager.expireOverdue(50, Instant.now());
    }
}
