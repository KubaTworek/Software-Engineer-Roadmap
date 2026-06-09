package com.example.newsfeed.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {

    @Query(value = """
            SELECT *
            FROM domain_events
            WHERE status IN ('PENDING', 'FAILED')
              AND next_attempt_at <= now()
            ORDER BY created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DomainEvent> findDueEvents(int limit);
}
