package com.example.paymentsystem.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    long countByPublishedAtIsNullAndDeadLetteredAtIsNull();

    @Query("select min(e.createdAt) from OutboxEvent e where e.publishedAt is null and e.deadLetteredAt is null")
    Instant oldestPendingCreatedAt();
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
              AND dead_lettered_at IS NULL
              AND next_attempt_at <= :now
            ORDER BY created_at
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPublishableBatch(@Param("now") Instant now);
}
