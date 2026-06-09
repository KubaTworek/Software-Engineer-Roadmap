package pl.jakubtworek.chatsystem.media;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        String fileName,
        String mimeType,
        long sizeBytes,
        AttachmentStatus status,
        String uploadUrl,
        String downloadUrl,
        Instant createdAt,
        Instant uploadedAt
) {
    public static AttachmentResponse from(Attachment attachment, String uploadUrl, String downloadUrl) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getStatus(),
                uploadUrl,
                downloadUrl,
                attachment.getCreatedAt(),
                attachment.getUploadedAt()
        );
    }
}
