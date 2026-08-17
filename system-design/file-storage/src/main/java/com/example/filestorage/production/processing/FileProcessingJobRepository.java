package com.example.filestorage.production.processing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileProcessingJobRepository extends JpaRepository<FileProcessingJob, UUID> {
    Optional<FileProcessingJob> findByFileIdAndJobType(UUID fileId, FileProcessingJobType jobType);
    List<FileProcessingJob> findAllByStatusOrderByCreatedAtAsc(FileProcessingJobStatus status, Pageable pageable);
    long countByStatus(FileProcessingJobStatus status);
}
