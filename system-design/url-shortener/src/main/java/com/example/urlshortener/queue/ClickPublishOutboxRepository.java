package com.example.urlshortener.queue;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickPublishOutboxRepository extends JpaRepository<ClickPublishOutboxMessage, String> {
    @Query(value = """
            SELECT * FROM click_publish_outbox
            WHERE published_at IS NULL
              AND dead_lettered_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (locked_until IS NULL OR locked_until < :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ClickPublishOutboxMessage> lockClaimable(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    long countByPublishedAtIsNull();
}
