package com.example.videostreaming.qoe;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/** Jawna retencja ogranicza koszt i czas ekspozycji danych telemetrycznych. */
@Component
public class QoeRetentionJob {
    private final QoeEventRepository events;
    private final Clock clock;
    private final int retentionDays;

    public QoeRetentionJob(QoeEventRepository events, Clock clock,
                           @Value("${app.qoe.retention-days:30}") int retentionDays) {
        this.events = events;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.qoe.retention-cron:0 30 3 * * *}")
    @Transactional
    public int deleteExpiredEvents() {
        return events.deleteOlderThan(clock.instant().minus(Duration.ofDays(retentionDays)));
    }
}
