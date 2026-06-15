package com.example.filestorage.production.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
    Page<DeadLetterEvent> findAllByOrderByFailedAtDesc(Pageable pageable);
}
