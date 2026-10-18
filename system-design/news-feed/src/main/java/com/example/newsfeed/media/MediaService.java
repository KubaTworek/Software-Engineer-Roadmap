package com.example.newsfeed.media;

import com.example.newsfeed.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.UUID;

@Service
public class MediaService {
    private static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4");
    private final MediaAssetRepository repository;
    private final String publicBaseUrl;

    public MediaService(MediaAssetRepository repository, @Value("${newsfeed.media.public-base-url:http://localhost:9000/news-feed-media}") String publicBaseUrl) {
        this.repository = repository; this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public MediaUploadResponse createUpload(User user, CreateMediaUploadRequest request) {
        if (!ALLOWED_TYPES.contains(request.mediaType())) {
            throw new IllegalArgumentException("Unsupported media type.");
        }
        if (request.sizeBytes() == null || request.sizeBytes() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Upload exceeds the 25 MiB educational limit.");
        }
        UUID id = UUID.randomUUID();
        String objectKey = user.getId() + "/" + id + "-" + request.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String publicUrl = publicBaseUrl + "/" + objectKey;
        // pending_upload nie jest publiczne; po uploadzie skaner musi przełączyć asset na READY.
        repository.save(new MediaAsset(id, user.getId(), objectKey, request.mediaType(), "pending_upload", publicUrl, Instant.now(), Instant.now()));
        // Local-friendly fake presigned URL. Production: S3/MinIO presigned PUT URL.
        return new MediaUploadResponse(id, publicUrl + "?upload=true", publicUrl, "pending_upload");
    }
}
