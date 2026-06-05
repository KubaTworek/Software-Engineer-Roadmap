package com.example.videostreaming.upload;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class UploadDtos {
    private UploadDtos() {}
    public record CreateUploadRequest(@NotBlank String filename, @NotBlank String contentType, @Min(1) long sizeBytes) {}
    public record CreateUploadResponse(UUID uploadId, UUID videoId, String objectKey, String uploadUrl, String method, int expiresInMinutes) {}
    public record CompleteUploadResponse(UUID uploadId, UUID videoId, String status, String transcodingStatus) {}
}
