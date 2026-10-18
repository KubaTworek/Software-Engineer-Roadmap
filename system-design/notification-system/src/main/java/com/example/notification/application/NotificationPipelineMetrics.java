package com.example.notification.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Snapshot metryk backlogu. Nie używa tenantId, userId ani jobId jako tagów,
 * dzięki czemu cardinality pozostaje ograniczona także przy dużej skali.
 */
@Component
public class NotificationPipelineMetrics {
    private final Ports.NotificationJobRepository jobs;
    private final Ports.OutboxRepository outbox;
    private final Clock clock;
    private final AtomicLong oldestReadyAge = new AtomicLong();
    private final AtomicLong pendingOutbox = new AtomicLong();
    private final AtomicLong deadLetters = new AtomicLong();

    public NotificationPipelineMetrics(Ports.NotificationJobRepository jobs, Ports.OutboxRepository outbox,
                                       Clock clock, MeterRegistry registry) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.clock = clock;
        Gauge.builder("notification_oldest_ready_job_age_seconds", oldestReadyAge, AtomicLong::get)
                .register(registry);
        Gauge.builder("notification_outbox_pending", pendingOutbox, AtomicLong::get).register(registry);
        Gauge.builder("notification_dlq_size", deadLetters, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${notification.metrics.snapshot-ms:5000}")
    public void refresh() {
        var now = clock.instant();
        oldestReadyAge.set(jobs.oldestReadyAt(now)
                .map(oldest -> Math.max(0, Duration.between(oldest, now).toSeconds())).orElse(0L));
        pendingOutbox.set(outbox.countPending());
        deadLetters.set(jobs.countDeadLetter());
    }
}
