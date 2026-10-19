package com.example.filestorage.production.processing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface FileProcessingJobRepository extends JpaRepository<FileProcessingJob, UUID> {
    Optional<FileProcessingJob> findByFileIdAndVersionIdAndJobType(
            UUID fileId, UUID versionId, FileProcessingJobType jobType);
    List<FileProcessingJob> findAllByStatusOrderByCreatedAtAsc(FileProcessingJobStatus status, Pageable pageable);
    long countByStatus(FileProcessingJobStatus status);

    @Query(value = """
            SELECT * FROM file_processing_jobs
            WHERE next_attempt_at <= :now
              AND (status = 'PENDING' OR (status = 'RUNNING' AND claimed_at < :staleBefore))
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<FileProcessingJob> claimableBatch(
            @Param("now") Instant now,
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize);

    @Query("select distinct job.resultObjectKey from FileProcessingJob job where job.resultObjectKey is not null")
    List<String> findAllResultObjectKeys();
}
