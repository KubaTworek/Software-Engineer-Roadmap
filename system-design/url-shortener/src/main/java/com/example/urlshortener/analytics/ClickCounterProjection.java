package com.example.urlshortener.analytics;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Near-real-time Redis counters are derived data and must never lead committed SQL state. */
@Component
public class ClickCounterProjection {
    private final ClickCounterService counters;

    public ClickCounterProjection(ClickCounterService counters) {
        this.counters = counters;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void update(ClickAnalyticsCommitted event) {
        counters.incrementTotal(event.shortCode());
        counters.incrementDaily(event.shortCode(), event.date());
    }
}
