package com.example.urlshortener.analytics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Raw PII-adjacent events expire independently from longer-lived daily aggregates. */
@Component
public class AnalyticsRetentionJob {
    private final ClickEventRepository events;
    private final Clock clock;
    private final Duration retention;
    public AnalyticsRetentionJob(ClickEventRepository events, Clock clock,
            @Value("${app.analytics.raw-event-retention:P30D}") Duration retention) {
        this.events = events; this.clock = clock; this.retention = retention;
    }
    @Scheduled(fixedDelayString = "${app.analytics.retention-cleanup-delay:PT6H}")
    public void deleteExpiredRawEvents() {
        events.deleteByClickedAtBefore(Instant.now(clock).minus(retention));
    }
}
