package com.example.filestorage.upload;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CompleteChunkRequest(
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256 must be a 64-char hex SHA-256", flags = Pattern.Flag.CASE_INSENSITIVE)
        String sha256,
        @Positive long sizeBytes
) {}
