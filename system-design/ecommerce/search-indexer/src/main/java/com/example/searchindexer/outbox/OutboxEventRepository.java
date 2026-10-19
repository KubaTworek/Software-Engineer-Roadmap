package com.example.searchindexer.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e " +
            "where e.eventType in ('ProductCreated', 'ProductUpdated', 'InventoryUpdated') " +
            "and e.id not in (select p.outboxEventId from ProcessedSearchEvent p) " +
            "order by e.id asc")
    List<OutboxEvent> findUnprocessedSearchEvents();
}
