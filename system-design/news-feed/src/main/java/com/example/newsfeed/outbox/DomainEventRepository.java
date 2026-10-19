package com.example.newsfeed.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {

    @Query(value = """
            SELECT id
            FROM domain_events
            WHERE status IN ('PENDING', 'FAILED', 'CLAIMED')
              AND next_attempt_at <= now()
            ORDER BY created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDueEventIds(int limit);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DomainEvent e WHERE e.id = :id")
    java.util.Optional<DomainEvent> findByIdForUpdate(UUID id);

    @Modifying
    @Query("DELETE FROM DomainEvent e WHERE e.status = com.example.newsfeed.outbox.DomainEventStatus.PROCESSED " +
            "AND e.processedAt < :cutoff")
    int deleteProcessedBefore(java.time.Instant cutoff);

    @Query("SELECT min(e.createdAt) FROM DomainEvent e WHERE e.status <> com.example.newsfeed.outbox.DomainEventStatus.PROCESSED")
    java.util.Optional<java.time.Instant> oldestUnprocessedCreatedAt();

    long countByStatus(DomainEventStatus status);
}
