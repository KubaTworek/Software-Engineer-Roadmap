package pl.jakubtworek.chatsystem.media;

import jakarta.persistence.*;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachments_owner", columnList = "owner_id"),
        @Index(name = "idx_attachments_upload_token", columnList = "upload_token")
})
public class Attachment {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, unique = true)
    private String storageKey;

    @Column(name = "upload_token", nullable = false, unique = true)
    private String uploadToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttachmentStatus status = AttachmentStatus.UPLOAD_PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant uploadedAt;

    protected Attachment() {}

    public Attachment(AppUser owner, String fileName, String mimeType, long sizeBytes, String storageKey, String uploadToken) {
        this.owner = owner;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadToken = uploadToken;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markUploaded(long actualSizeBytes) {
        this.sizeBytes = actualSizeBytes;
        this.status = AttachmentStatus.UPLOADED;
        this.uploadedAt = Instant.now();
    }

    public void block() {
        this.status = AttachmentStatus.BLOCKED;
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public String getUploadToken() { return uploadToken; }
    public AttachmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUploadedAt() { return uploadedAt; }
}
