package com.example.filestorage.production.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
    long countByStatus(OutboxStatus status);

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE next_attempt_at <= :now
              AND (status = 'PENDING' OR (status = 'PROCESSING' AND claimed_at < :staleBefore))
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<OutboxEvent> claimableBatch(@Param("now") Instant now,
                                     @Param("staleBefore") Instant staleBefore,
                                     @Param("batchSize") int batchSize);
}
