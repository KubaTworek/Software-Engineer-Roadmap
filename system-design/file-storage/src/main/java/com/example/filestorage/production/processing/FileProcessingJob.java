package com.example.filestorage.production.processing;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_processing_jobs")
public class FileProcessingJob {
    @Id
    private UUID id;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private FileProcessingJobType jobType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileProcessingJobStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    @Column(name = "result_object_key")
    private String resultObjectKey;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    protected FileProcessingJob() {}

    public FileProcessingJob(UUID fileId, UUID versionId, FileProcessingJobType jobType) {
        this.id = UUID.randomUUID();
        this.fileId = fileId;
        this.versionId = versionId;
        this.jobType = jobType;
        this.status = FileProcessingJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.nextAttemptAt = this.createdAt;
    }

    public void markRunning() {
        claim(Instant.now());
    }

    public void claim(Instant now) {
        status = FileProcessingJobStatus.RUNNING;
        attempts++;
        claimedAt = now;
        updatedAt = now;
    }

    public void markCompleted(String resultObjectKey) {
        this.status = FileProcessingJobStatus.COMPLETED;
        this.resultObjectKey = resultObjectKey;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
        this.lastError = null;
        this.claimedAt = null;
    }

    public void markFailed(String error) {
        this.status = FileProcessingJobStatus.FAILED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public void retryOrFail(String error, Instant now, int maxAttempts) {
        lastError = error;
        updatedAt = now;
        claimedAt = null;
        if (attempts >= maxAttempts) {
            status = FileProcessingJobStatus.FAILED;
            return;
        }
        status = FileProcessingJobStatus.PENDING;
        nextAttemptAt = now.plus(Duration.ofSeconds(Math.min(300, 1L << Math.min(attempts, 8))));
    }

    public UUID getId() { return id; }
    public UUID getFileId() { return fileId; }
    public UUID getVersionId() { return versionId; }
    public FileProcessingJobType getJobType() { return jobType; }
    public FileProcessingJobStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getResultObjectKey() { return resultObjectKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
}
