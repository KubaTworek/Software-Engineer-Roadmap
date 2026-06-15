package com.example.filestorage.production.processing;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_processing_jobs")
public class FileProcessingJob {
    @Id
    private UUID id;
    @Column(name = "file_id", nullable = false)
    private UUID fileId;
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

    protected FileProcessingJob() {}

    public FileProcessingJob(UUID fileId, FileProcessingJobType jobType) {
        this.id = UUID.randomUUID();
        this.fileId = fileId;
        this.jobType = jobType;
        this.status = FileProcessingJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markRunning() {
        this.status = FileProcessingJobStatus.RUNNING;
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String resultObjectKey) {
        this.status = FileProcessingJobStatus.COMPLETED;
        this.resultObjectKey = resultObjectKey;
        this.completedAt = Instant.now();
        this.updatedAt = this.completedAt;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = FileProcessingJobStatus.FAILED;
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFileId() { return fileId; }
    public FileProcessingJobType getJobType() { return jobType; }
    public FileProcessingJobStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public String getResultObjectKey() { return resultObjectKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
