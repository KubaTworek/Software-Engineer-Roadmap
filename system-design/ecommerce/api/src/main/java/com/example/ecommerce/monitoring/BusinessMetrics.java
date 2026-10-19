package com.example.ecommerce.monitoring;

import com.example.ecommerce.outbox.OutboxEventRepository;
import com.example.ecommerce.outbox.OutboxEventStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Niskokardynalny kontrakt telemetrii krytycznego flow. Identyfikatory orderu,
 * klienta i SKU należą do logów/trace'ów, nigdy do tagów metryk.
 */
@Component
public class BusinessMetrics {
    private final Counter checkoutStarted;
    private final Counter checkoutSucceeded;
    private final Counter paymentSucceeded;
    private final Counter paymentFailed;
    private final Counter outboxPublished;
    private final Counter outboxFailed;
    private final Counter outboxDead;
    private final Counter integrationRetried;
    private final Counter webhookRejected;
    private final Counter reconciliationResolved;
    private final Counter reconciliationStillUnknown;
    private final Timer checkoutLatency;
    private final Timer inventoryLockWait;
    private final Timer outboxPublishLatency;

    public BusinessMetrics(MeterRegistry registry, OutboxEventRepository outbox, Clock clock) {
        checkoutStarted = counter(registry, "ecommerce_checkout_started_total", "Checkout attempts");
        checkoutSucceeded = counter(registry, "ecommerce_checkout_succeeded_total", "Successful checkouts");
        paymentSucceeded = counter(registry, "ecommerce_payment_succeeded_total", "Successful payments");
        paymentFailed = counter(registry, "ecommerce_payment_failed_total", "Failed payments");
        outboxPublished = counter(registry, "ecommerce_outbox_published_total", "Published outbox events");
        outboxFailed = counter(registry, "ecommerce_outbox_failed_total", "Failed outbox attempts");
        outboxDead = counter(registry, "ecommerce_outbox_dead_total", "Events moved to DEAD");
        integrationRetried = counter(registry, "ecommerce_integration_retried_total", "Integration retries");
        webhookRejected = counter(registry, "ecommerce_payment_webhook_rejected_total", "Rejected PSP callbacks");
        reconciliationResolved = counter(registry, "ecommerce_payment_reconciliation_resolved_total",
                "Unknown payment outcomes resolved from the provider");
        reconciliationStillUnknown = counter(registry, "ecommerce_payment_reconciliation_unknown_total",
                "Payment lookups that still have no terminal provider result");
        checkoutLatency = Timer.builder("ecommerce_checkout_duration")
                .description("End-to-end local checkout transaction latency")
                .publishPercentileHistogram().register(registry);
        inventoryLockWait = Timer.builder("ecommerce_inventory_lock_wait")
                .description("Time spent waiting for a pessimistic inventory row lock")
                .publishPercentileHistogram().register(registry);
        outboxPublishLatency = Timer.builder("ecommerce_outbox_publish_duration")
                .description("Outbox batch publishing latency")
                .publishPercentileHistogram().register(registry);

        Gauge.builder("ecommerce_outbox_backlog", outbox,
                        repository -> repository.countByStatus(OutboxEventStatus.NEW)
                                + repository.countByStatus(OutboxEventStatus.FAILED))
                .description("Publishable outbox records").register(registry);
        Gauge.builder("ecommerce_outbox_oldest_event_age_seconds", outbox,
                        repository -> repository
                                .findFirstByStatusInOrderByCreatedAtAsc(
                                        List.of(OutboxEventStatus.NEW, OutboxEventStatus.FAILED))
                                .map(event -> Math.max(0,
                                        Duration.between(event.getCreatedAt(), clock.instant()).toSeconds()))
                                .orElse(0L))
                .description("Age of the oldest publishable outbox event").register(registry);
    }

    private Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    public void checkoutStarted() { checkoutStarted.increment(); }
    public void checkoutSucceeded() { checkoutSucceeded.increment(); }
    public void paymentSucceeded() { paymentSucceeded.increment(); }
    public void paymentFailed() { paymentFailed.increment(); }
    public void outboxPublished() { outboxPublished.increment(); }
    public void integrationRetried() { integrationRetried.increment(); }
    public void webhookRejected() { webhookRejected.increment(); }
    public void paymentReconciliationResolved() { reconciliationResolved.increment(); }
    public void paymentReconciliationStillUnknown() { reconciliationStillUnknown.increment(); }
    public void recordCheckout(Duration duration) { checkoutLatency.record(duration); }
    public void recordInventoryLockWait(Duration duration) { inventoryLockWait.record(duration); }
    public void recordOutboxPublish(Duration duration) { outboxPublishLatency.record(duration); }

    public void outboxFailed(boolean dead) {
        outboxFailed.increment();
        if (dead) {
            outboxDead.increment();
        }
    }
}
