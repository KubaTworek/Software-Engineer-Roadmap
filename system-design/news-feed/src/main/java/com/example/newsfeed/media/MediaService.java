package com.example.newsfeed.media;

import com.example.newsfeed.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.UUID;

@Service
public class MediaService {
    private final MediaAssetRepository repository;
    private final String publicBaseUrl;

    public MediaService(MediaAssetRepository repository, @Value("${newsfeed.media.public-base-url:http://localhost:9000/news-feed-media}") String publicBaseUrl) {
        this.repository = repository; this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public MediaUploadResponse createUpload(User user, CreateMediaUploadRequest request) {
        UUID id = UUID.randomUUID();
        String objectKey = user.getId() + "/" + id + "-" + request.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String publicUrl = publicBaseUrl + "/" + objectKey;
        repository.save(new MediaAsset(id, user.getId(), objectKey, request.mediaType(), "pending_upload", publicUrl, Instant.now(), Instant.now()));
        // Local-friendly fake presigned URL. Production: S3/MinIO presigned PUT URL.
        return new MediaUploadResponse(id, publicUrl + "?upload=true", publicUrl, "pending_upload");
    }
}
