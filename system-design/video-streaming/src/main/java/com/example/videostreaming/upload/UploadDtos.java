package com.example.videostreaming.upload;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UploadDtos {
    private UploadDtos() {}
    public record CreateUploadRequest(
            @NotBlank @Size(max = 255) String filename,
            @NotBlank @Pattern(regexp = "video/(mp4|quicktime|x-matroska|webm)") String contentType,
            @Min(1) @Max(53_687_091_200L) long sizeBytes
    ) {}
    public record CreateUploadResponse(UUID uploadId, UUID videoId, String objectKey, String uploadUrl, String method, int expiresInMinutes) {}
    public record CompleteUploadResponse(UUID uploadId, UUID videoId, String status, String transcodingStatus) {}
}
