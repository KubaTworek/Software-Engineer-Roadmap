package com.example.filestorage.production.backup;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BackupRunRepository extends JpaRepository<BackupRun, UUID> {
    Page<BackupRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
