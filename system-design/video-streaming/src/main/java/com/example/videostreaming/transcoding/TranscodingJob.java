package com.example.videostreaming.transcoding;

import com.example.videostreaming.catalog.Video;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.Duration;
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
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "lease_token", nullable = false)
    private long leaseToken;
    @Column(name = "lease_until")
    private Instant leaseUntil;

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
    public long getLeaseToken() { return leaseToken; }
    public Instant getLeaseUntil() { return leaseUntil; }

    /**
     * Lease pozwala odzyskać job po śmierci workera, a rosnący token odcina
     * spóźniony rezultat poprzedniego właściciela. @Version rozstrzyga wyścig
     * dwóch instancji próbujących uzyskać lease w tej samej chwili.
     */
    public long claim(Instant now, Duration duration) {
        if (status == TranscodingJobStatus.COMPLETED || status == TranscodingJobStatus.DEAD_LETTER) {
            throw new IllegalStateException("Terminal transcoding job cannot be claimed");
        }
        if (status == TranscodingJobStatus.RUNNING && leaseUntil != null && leaseUntil.isAfter(now)) {
            throw new IllegalStateException("Transcoding job already has an active lease");
        }
        this.status = TranscodingJobStatus.RUNNING;
        this.attempts++;
        this.leaseToken++;
        this.leaseUntil = now.plus(duration);
        touch(now);
        return leaseToken;
    }

    public void completed(long token, Instant now) {
        assertFence(token);
        this.status = TranscodingJobStatus.COMPLETED;
        this.completedAt = now;
        this.leaseUntil = null;
        touch(now);
    }
    public void running() { claim(Instant.now(), Duration.ofHours(2)); }
    public void completed() { completed(leaseToken, Instant.now()); }
    public void retrying(String message) { this.status = TranscodingJobStatus.RETRYING; this.errorMessage = message; touch(); }
    public void failed(String message) { this.status = TranscodingJobStatus.FAILED; this.errorMessage = message; touch(); }
    public void deadLetter(String message) { this.status = TranscodingJobStatus.DEAD_LETTER; this.errorMessage = message; touch(); }
    public void retrying(long token, String message, Instant now) {
        assertFence(token);
        this.status = TranscodingJobStatus.RETRYING;
        this.errorMessage = message;
        this.leaseUntil = null;
        touch(now);
    }

    public void deadLetter(long token, String message, Instant now) {
        assertFence(token);
        this.status = TranscodingJobStatus.DEAD_LETTER;
        this.errorMessage = message;
        this.leaseUntil = null;
        touch(now);
    }

    private void assertFence(long token) {
        if (token != leaseToken || status != TranscodingJobStatus.RUNNING) {
            throw new IllegalStateException("Stale transcoding worker fencing token");
        }
    }

    private void touch() { touch(Instant.now()); }
    private void touch(Instant now) { this.updatedAt = now; }
}
