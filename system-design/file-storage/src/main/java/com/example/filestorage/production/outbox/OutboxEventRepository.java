package com.example.filestorage.production.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
    long countByStatus(OutboxStatus status);
}
