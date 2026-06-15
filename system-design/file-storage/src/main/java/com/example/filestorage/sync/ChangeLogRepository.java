package com.example.filestorage.sync;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {
    List<ChangeLog> findAllByOwnerIdAndIdGreaterThanOrderByIdAsc(UUID ownerId, Long id, Pageable pageable);
}
