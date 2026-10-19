package com.example.urlshortener.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

public interface UrlProjectionOutboxRepository extends JpaRepository<UrlProjectionOutboxMessage, String> {
    List<UrlProjectionOutboxMessage> findTop100BySentAtIsNullOrderByCreatedAtAsc();
    long countBySentAtIsNullAndDeadLetteredAtIsNull();
    long countByDeadLetteredAtIsNotNull();

    @Query(value = """
            SELECT * FROM url_projection_outbox
            WHERE sent_at IS NULL
              AND dead_lettered_at IS NULL
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
              AND (locked_until IS NULL OR locked_until < :now)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UrlProjectionOutboxMessage> lockClaimable(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update UrlProjectionOutboxMessage m set m.sentAt = :sentAt where m.eventId = :eventId and m.sentAt is null")
    int markSent(@Param("eventId") String eventId, @Param("sentAt") Instant sentAt);
}
