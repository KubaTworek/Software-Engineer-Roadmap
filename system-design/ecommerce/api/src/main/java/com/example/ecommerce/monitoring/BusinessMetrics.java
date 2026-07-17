package com.example.ecommerce.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
    private final Counter checkoutStarted;
    private final Counter checkoutSucceeded;
    private final Counter paymentSucceeded;
    private final Counter paymentFailed;
    private final Counter outboxPublished;
    private final Counter integrationRetried;

    public BusinessMetrics(MeterRegistry registry) {
        this.checkoutStarted = Counter.builder("ecommerce_checkout_started_total").description("Checkout attempts").register(registry);
        this.checkoutSucceeded = Counter.builder("ecommerce_checkout_succeeded_total").description("Successful checkout creations").register(registry);
        this.paymentSucceeded = Counter.builder("ecommerce_payment_succeeded_total").description("Successful payments").register(registry);
        this.paymentFailed = Counter.builder("ecommerce_payment_failed_total").description("Failed payments").register(registry);
        this.outboxPublished = Counter.builder("ecommerce_outbox_published_total").description("Published outbox events").register(registry);
        this.integrationRetried = Counter.builder("ecommerce_integration_retried_total").description("Integration retry attempts").register(registry);
    }
    public void checkoutStarted(){ checkoutStarted.increment(); }
    public void checkoutSucceeded(){ checkoutSucceeded.increment(); }
    public void paymentSucceeded(){ paymentSucceeded.increment(); }
    public void paymentFailed(){ paymentFailed.increment(); }
    public void outboxPublished(){ outboxPublished.increment(); }
    public void integrationRetried(){ integrationRetried.increment(); }
}
