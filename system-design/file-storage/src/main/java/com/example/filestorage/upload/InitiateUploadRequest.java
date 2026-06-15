package com.example.filestorage.upload;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record InitiateUploadRequest(
        UUID parentFolderId,
        @NotBlank String filename,
        String contentType,
        @Positive long totalSizeBytes,
        @Positive long chunkSizeBytes,
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "expectedSha256 must be a 64-char hex SHA-256", flags = Pattern.Flag.CASE_INSENSITIVE)
        String expectedSha256
) {}
