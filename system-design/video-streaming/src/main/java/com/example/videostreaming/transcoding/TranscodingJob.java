package com.example.videostreaming.transcoding;

import com.example.videostreaming.catalog.Video;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcoding_jobs")
public class TranscodingJob {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;
    @Column(name = "source_object_key", nullable = false)
    private String sourceObjectKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscodingJobStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected TranscodingJob() {}

    public TranscodingJob(Video video, String sourceObjectKey) {
        this.id = UUID.randomUUID();
        this.video = video;
        this.sourceObjectKey = sourceObjectKey;
        this.status = TranscodingJobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public Video getVideo() { return video; }
    public String getSourceObjectKey() { return sourceObjectKey; }
    public TranscodingJobStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getErrorMessage() { return errorMessage; }

    public void running() { this.status = TranscodingJobStatus.RUNNING; this.attempts++; touch(); }
    public void completed() { this.status = TranscodingJobStatus.COMPLETED; this.completedAt = Instant.now(); touch(); }
    public void retrying(String message) { this.status = TranscodingJobStatus.RETRYING; this.errorMessage = message; touch(); }
    public void failed(String message) { this.status = TranscodingJobStatus.FAILED; this.errorMessage = message; touch(); }
    public void deadLetter(String message) { this.status = TranscodingJobStatus.DEAD_LETTER; this.errorMessage = message; touch(); }
    private void touch() { this.updatedAt = Instant.now(); }
}
