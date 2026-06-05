package com.example.videostreaming.upload;

import com.example.videostreaming.catalog.Video;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploads")
public class Upload {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;
    @Column(name = "object_key", nullable = false)
    private String objectKey;
    @Column(nullable = false)
    private String filename;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    protected Upload() {}

    public Upload(Video video, String objectKey, String filename, String contentType, long sizeBytes) {
        this.id = UUID.randomUUID();
        this.video = video;
        this.objectKey = objectKey;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = UploadStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Video getVideo() { return video; }
    public String getObjectKey() { return objectKey; }
    public UploadStatus getStatus() { return status; }
    public void complete() { this.status = UploadStatus.COMPLETED; this.completedAt = Instant.now(); }
}
