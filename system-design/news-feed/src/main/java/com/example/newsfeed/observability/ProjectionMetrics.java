package com.example.newsfeed.observability;

import com.example.newsfeed.events.DeadLetterEventRepository;
import com.example.newsfeed.feed.JpaFeedInboxRepository;
import com.example.newsfeed.outbox.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scrape Prometheusa nie odpytuje bazy. Harmonogram tworzy tani snapshot
 * operational state, a gauge odczytuje wyłącznie atomowe wartości.
 */
@Component
public class ProjectionMetrics {
    private final DomainEventRepository outbox;
    private final DeadLetterEventRepository dlq;
    private final JpaFeedInboxRepository inbox;
    private final Clock clock;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong dlqSize = new AtomicLong();
    private final AtomicLong inboxSize = new AtomicLong();

    public ProjectionMetrics(DomainEventRepository outbox, DeadLetterEventRepository dlq,
                             JpaFeedInboxRepository inbox, Clock clock, MeterRegistry registry) {
        this.outbox=outbox; this.dlq=dlq; this.inbox=inbox; this.clock=clock;
        Gauge.builder("newsfeed.outbox.pending", pending, AtomicLong::get).register(registry);
        Gauge.builder("newsfeed.outbox.failed", failed, AtomicLong::get).register(registry);
        Gauge.builder("newsfeed.outbox.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get).register(registry);
        Gauge.builder("newsfeed.dlq.size", dlqSize, AtomicLong::get).register(registry);
        Gauge.builder("newsfeed.feed.inbox.entries", inboxSize, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${newsfeed.metrics.projection-refresh-ms:15000}")
    public void refresh() {
        pending.set(outbox.countByStatus(DomainEventStatus.PENDING));
        failed.set(outbox.countByStatus(DomainEventStatus.FAILED));
        oldestAgeSeconds.set(outbox.oldestUnprocessedCreatedAt()
                .map(created -> Math.max(0, Duration.between(created, clock.instant()).toSeconds())).orElse(0L));
        dlqSize.set(dlq.count());
        inboxSize.set(inbox.count());
    }
}
