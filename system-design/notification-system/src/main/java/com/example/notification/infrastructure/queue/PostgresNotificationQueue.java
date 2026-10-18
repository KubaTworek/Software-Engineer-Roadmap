package com.example.notification.infrastructure.queue;

import com.example.notification.application.Ports;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Trwała kolejka oparta o tabelę jobów. Sam rekord jest źródłem prawdy, a
 * `lease_until` chroni przed równoległym claimem i umożliwia recovery po
 * śmierci procesu. `SKIP LOCKED` pozwala wielu instancjom pobierać różne joby.
 */
@Component
@Profile("postgres-runtime")
public class PostgresNotificationQueue implements Ports.NotificationQueue {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration lease;

    public PostgresNotificationQueue(JdbcTemplate jdbc, Clock clock,
                                     @Value("${notification.worker.lease-seconds:30}") long leaseSeconds) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.lease = Duration.ofSeconds(leaseSeconds);
    }

    @Override
    public void enqueue(UUID jobId) {
        // Zapis joba jest enqueue. Nie tworzymy drugiego, zawodnego sygnału w JVM.
    }

    @Override
    @Transactional
    public Optional<UUID> pollReadyJob() {
        Instant now = clock.instant();
        var ids = jdbc.queryForList("""
                SELECT job_id FROM notification_jobs
                WHERE status IN ('QUEUED','PROCESSING') AND next_attempt_at<=?
                  AND (lease_until IS NULL OR lease_until<?)
                ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1
                         WHEN 'NORMAL' THEN 2 ELSE 3 END, next_attempt_at, created_at
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, UUID.class, Timestamp.from(now), Timestamp.from(now));
        if (ids.isEmpty()) return Optional.empty();
        UUID id = ids.getFirst();
        jdbc.update("UPDATE notification_jobs SET lease_until=? WHERE job_id=?",
                Timestamp.from(now.plus(lease)), id);
        return Optional.of(id);
    }

    @Override
    public int size() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM notification_jobs WHERE status='QUEUED'",
                Integer.class);
        return count == null ? 0 : count;
    }
}
