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

    @Column(name = "upload_token", unique = true)
    private String uploadToken;

    @Column(name = "upload_expires_at", nullable = false)
    private Instant uploadExpiresAt;

    @Column(length = 64)
    private String checksum;

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
        this.uploadExpiresAt = Instant.now().plusSeconds(900);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markScanning(long actualSizeBytes) {
        if (Instant.now().isAfter(uploadExpiresAt)) throw new IllegalStateException("Upload capability expired");
        this.sizeBytes = actualSizeBytes;
        this.status = AttachmentStatus.SCANNING;
        this.uploadedAt = Instant.now();
        this.uploadToken = null;
    }

    public void markReady(String checksum) {
        if (status != AttachmentStatus.SCANNING) throw new IllegalStateException("Attachment is not being scanned");
        this.checksum = checksum;
        this.status = AttachmentStatus.READY;
    }

    public void reject() {
        this.status = AttachmentStatus.REJECTED;
        this.uploadToken = null;
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
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public String getChecksum() { return checksum; }
}
